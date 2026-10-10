/*
 * This file is part of SuperLyric.
 *
 * SuperLyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2025-2026 HChenX
 */
package com.hchen.superlyric.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Bundle;
import com.hchen.hooktool.ModuleData;
import com.hchen.superlyric.reload.ServiceEndpoint;
import com.hchen.superlyric.reload.ReloadResources;
import io.github.libxposed.api.XposedInterface;
import java.util.HashMap;

import androidx.annotation.NonNull;

import com.hchen.hooktool.AbsModule;
import com.hchen.hooktool.hook.AbsHook;
import com.hchen.processor.HookThis;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.libxposed.api.XposedModuleInterface;

/**
 * 代理并发布 Super Lyric 系统级服务到 system_server。
 *
 * @author 焕晨HChen
 */
@HookThis(targetPackage = "system", onSystemStarting = true)
public final class SuperLyricProxy extends AbsModule {
    private static SuperLyricService sSuperLyricService;
    private static ServiceEndpoint sEndpoint;
    private static Object sAms;
    private static Map<String, Object> reloadState;

    public static void failReload() { if (sEndpoint != null) sEndpoint.fail(); }

    public static void prepareRestore(Map<String, Object> state) { reloadState = state; }

    public static Map<String, Object> retireAndSave() {
        Map<String, Object> state = new HashMap<>();
        if (sEndpoint != null) {
            sEndpoint.beginRetirement();
            state.put("endpoint", sEndpoint.save());
        }
        state.put("ams", sAms);
        if (sSuperLyricService != null) state.put("service", sSuperLyricService.retireAndSave());
        sSuperLyricService = null;
        sAms = null;
        return state;
    }

    private void installEndpoint() {
        try {
            Method method = android.os.Binder.class.getDeclaredMethod("onTransact", int.class, Parcel.class, Parcel.class, int.class);
            ModuleData.getWrapper().hook(method).setId(ServiceEndpoint.HOOK_ID)
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(sEndpoint.hooker());
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("Binder dispatch method is unavailable", error);
        }
    }

    private void initializeService(Object ams, Bundle saved) {
        if (sSuperLyricService != null || ReloadResources.retiring) return;
        Context context = (Context) getField(ams, "mContext");
        if (context == null) throw new IllegalStateException("ActivityManager context is unavailable");
        sAms = ams;
        if (sEndpoint == null) sEndpoint = new ServiceEndpoint();
        installEndpoint();
        SuperLyricService next = new SuperLyricService(ams);
        next.restore(saved);
        sSuperLyricService = next;
        SystemPlayStateListener listener = new SystemPlayStateListener(context, next);
        next.setSystemPlayStateListener(listener);
        ReloadResources.onMain(() -> { listener.register(); return null; });
        sEndpoint.publish(next);
        next.replayActiveLyric();
        logI(tag, "SuperLyric service generation ready; clients retain their Binder endpoint");
    }

    @Override
    protected void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
    }

    @Override
    protected void onSystemServerStarting(@NonNull XposedModuleInterface.SystemServerStartingParam param) {
        hookAllMethod("com.android.server.am.ActivityManagerService",
            "systemReady",
            new AbsHook() {
                @Override
                public void after() {
                    try {
                        initializeService(getThisObject(), null);
                    } catch (Throwable e) {
                        logE(tag, "Failed to initialize super lyric system service.", e);
                    }
                }
            }
        );

        Method servicesMethod = findMethodIfExists("com.android.server.am.ActivityManagerService",
            "getCommonServicesLocked",
            boolean.class /* isolated */, boolean.class /* instant */
        );
        if (servicesMethod == null) {
            servicesMethod = findMethodIfExists(
                "com.android.server.am.ActivityManagerService",
                "getCommonServicesLocked",
                boolean.class /* isolated */
            );
        }

        Objects.requireNonNull(servicesMethod, "Failed to load super lyric service, [ActivityManagerService#getCommonServicesLocked()] not found.");
        hook(servicesMethod,
            new AbsHook() {
                @Override
                public void after() {
                    if (sSuperLyricService == null) return;

                    boolean isolated = (boolean) getArg(0);
                    if (isolated) return;

                    @SuppressWarnings("unchecked")
                    Map<String, IBinder> mAppBindArgs = (Map<String, IBinder>) getResult();
                    if (mAppBindArgs.get("super_lyric") != sEndpoint.binder) {
                        mAppBindArgs.put("super_lyric", sEndpoint.binder);
                        logI(tag, "Exposed super_lyric binder service to common services.");
                    }
                }
            }
        );

        Method appDiedLockedMethod = findMethodIfExists("com.android.server.am.ActivityManagerService",
            "appDiedLocked",
            "com.android.server.am.ProcessRecord" /* app */, int.class /* pid */, "android.app.IApplicationThread" /* thread */,
            boolean.class /* fromBinderDied */, String.class /* reason */
        );
        if (appDiedLockedMethod == null) {
            appDiedLockedMethod = findMethodIfExists(
                "com.android.server.am.ActivityManagerService",
                "appDiedLocked",
                "com.android.server.am.ProcessRecord" /* app */, int.class /* pid */, "android.app.IApplicationThread" /* thread */,
                boolean.class /* fromBinderDied */, String.class /* reason */, int.class, int.class
            );
        }

        Objects.requireNonNull(appDiedLockedMethod, "Failed to load super lyric service, [ActivityManagerService#appDiedLocked()] not found.");
        hook(appDiedLockedMethod,
            new AbsHook() {
                @Override
                public void after() {
                    if (sSuperLyricService == null) return;

                    Object mProcLock = getField(getThisObject(), "mProcLock");
                    if (mProcLock == null) return;

                    synchronized (mProcLock) {
                        Object app = getArg(0);
                        ApplicationInfo info = (ApplicationInfo) getField(app, "info");
                        if (info == null) return;

                        int pid = (int) getArg(1);
                        boolean isKilled = (boolean) Optional.ofNullable(getField(app, "mKilled")).orElse(true);
                        if (isKilled) {
                            sSuperLyricService.onProcessDied(info.packageName, pid);
                        }
                    }
                }
            }
        );
        if (reloadState != null) {
            Map<String, Object> saved = reloadState;
            reloadState = null;
            @SuppressWarnings("unchecked")
            Map<String, Object> endpointState = (Map<String, Object>) saved.get("endpoint");
            if (endpointState != null) sEndpoint = new ServiceEndpoint(endpointState);
            Object ams = saved.get("ams");
            if (ams != null) initializeService(ams, (Bundle) saved.get("service"));
        }

    }
}
