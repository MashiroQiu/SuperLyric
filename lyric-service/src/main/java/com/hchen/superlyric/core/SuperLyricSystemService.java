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
import android.util.ArrayMap;

import androidx.annotation.NonNull;

import com.hchen.hooktool.core.CoreTool;
import com.hchen.hooktool.hook.AbsHook;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import io.github.libxposed.api.XposedModuleInterface;

/**
 * 在 system_server 中创建运行时与两代 Binder 服务，并安装 AMS 钩子。
 *
 * 锚点缺失时只记日志，不下发服务。
 */
public final class SuperLyricSystemService extends CoreTool {
    private static final String TAG = "SuperLyricSystemService";

    private static Object sAms;
    private static com.hchen.superlyric.reload.ServiceEndpoint legacyEndpoint;
    private static com.hchen.superlyric.reload.ServiceEndpoint v4Endpoint;
    private static Map<String, Object> restoring;
    private static volatile LyricRuntime sRuntime;
    private static volatile SuperLyricService sSuperLyricService;
    private static volatile SuperLyricV4Service sSuperLyricV4Service;

    /** attachApplicationLocked 与 getCommonServicesLocked 在同一线程同步执行。 */
    private static final ThreadLocal<Integer> sBindUid = new ThreadLocal<>();

    private static volatile boolean sProcLockMissingLogged;
    private static volatile boolean sUidMissingLogged;

    public void install(@NonNull XposedModuleInterface.SystemServerStartingParam param) {
        hookAllMethod("com.android.server.am.ActivityManagerService",
            "systemReady",
            new AbsHook() {
                @Override
                public void after() {
                    initialize(getThisObject(), null);
                }
            }
        );

        attachApplicationLocked();
        hookCommonServices();
        hookAppDiedLocked();
        Map<String, Object> state = restoring;
        if (state != null && state.get("ams") != null) {
            restoring = null;
            initialize(state.get("ams"), state);
        }
    }

    public static void prepareRestore(Map<String, Object> state) { restoring = state; }

    public static void failReload() {
        if (legacyEndpoint != null) legacyEndpoint.fail();
        if (v4Endpoint != null) v4Endpoint.fail();
    }

    public static Map<String, Object> retireAndSave() {
        Map<String, Object> state = new HashMap<>();
        if (legacyEndpoint != null) { legacyEndpoint.beginRetirement(); state.put("legacy", legacyEndpoint.save()); }
        if (v4Endpoint != null) { v4Endpoint.beginRetirement(); state.put("v4", v4Endpoint.save()); }
        state.put("ams", sAms);
        if (sRuntime != null) state.put("runtime", sRuntime.retireAndSave());
        AccessPolicy.closeForReload();
        sRuntime = null; sSuperLyricService = null; sSuperLyricV4Service = null; sAms = null;
        return state;
    }

    @SuppressWarnings("unchecked")
    private static void initialize(Object ams, Map<String, Object> saved) {
        if (sRuntime != null || com.hchen.superlyric.reload.ReloadResources.retiring) return;
        Context context = (Context) getField(ams, "mContext");
        if (context == null) throw new IllegalStateException("AMS context unavailable");
        sAms = ams;
        AccessPolicy.initialize(context);
        Map<String, Object> legacy = saved == null ? null : (Map<String, Object>) saved.get("legacy");
        if (legacy == null && saved != null) legacy = (Map<String, Object>) saved.get("endpoint");
        Map<String, Object> v4 = saved == null ? null : (Map<String, Object>) saved.get("v4");
        legacyEndpoint = legacy == null ? new com.hchen.superlyric.reload.ServiceEndpoint() : new com.hchen.superlyric.reload.ServiceEndpoint(legacy);
        v4Endpoint = v4 == null ? new com.hchen.superlyric.reload.ServiceEndpoint("com.hchen.superlyricapi.v4.ISuperLyricService") : new com.hchen.superlyric.reload.ServiceEndpoint(v4);
        installEndpoint(legacyEndpoint, "superlyric.service.endpoint");
        installEndpoint(v4Endpoint, "superlyric.service.v4");
        LyricRuntime runtime = new LyricRuntime(ams);
        android.os.Bundle runtimeState = saved == null ? null : (android.os.Bundle) saved.get("runtime");
        if (runtimeState == null && saved != null) runtimeState = (android.os.Bundle) saved.get("service");
        runtime.restore(runtimeState);
        sRuntime = runtime;
        sSuperLyricService = new SuperLyricService(runtime, v4Endpoint.binder);
        sSuperLyricV4Service = new SuperLyricV4Service(runtime);
        legacyEndpoint.publish(sSuperLyricService);
        v4Endpoint.publish(sSuperLyricV4Service);
        SystemPlayStateListener listener = new SystemPlayStateListener(context, runtime);
        runtime.setSystemPlayStateListener(listener);
        if (!listener.register()) throw new IllegalStateException("MediaSession listener unavailable");
        context.sendBroadcast(new android.content.Intent("com.hchen.superlyric.SERVICE_READY").setPackage("com.android.systemui"));
    }

    private static void installEndpoint(com.hchen.superlyric.reload.ServiceEndpoint endpoint, String id) {
        try {
            com.hchen.hooktool.ModuleData.getWrapper().hook(android.os.Binder.class.getDeclaredMethod("onTransact", int.class,
                android.os.Parcel.class, android.os.Parcel.class, int.class)).setId(id)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PASSTHROUGH).intercept(endpoint.hooker());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private void attachApplicationLocked() {
        try {
            Class<?> amsClass = findAmsClass();
            if (amsClass == null) return;

            hookAllMethod(amsClass,
                "attachApplicationLocked",
                new AbsHook() {
                    @Override
                    public void before() {
                        try {
                            // 这是 AMS 内部方法，Binder.getCallingUid() 永远是 system_server(1000)；
                            // 目标进程 UID 必须取参数表里的 uid（thread, pid, uid, ...）。
                            int uid = ((Number) getArg(2)).intValue();
                            sBindUid.set(uid);
                            AccessPolicy.invalidateUid(uid);
                        } catch (Throwable t) {
                            logE(TAG, "Failed to record bind UID for "
                                + getExecutable().getName() + ".", t);
                        }
                    }

                    @Override
                    public void after() {
                        sBindUid.remove();
                    }
                }
            );
        } catch (Throwable t) {
            logE(TAG, "Failed to hook attachApplicationLocked; access control will fail closed.", t);
        }
    }

    private void hookCommonServices() {
        try {
            Class<?> amsClass = findAmsClass();
            if (amsClass == null) return;

            Method target = null;
            for (Method method : amsClass.getDeclaredMethods()) {
                if ("getCommonServicesLocked".equals(method.getName()) &&
                    Map.class.isAssignableFrom(method.getReturnType())) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                logE(TAG, "ActivityManagerService#getCommonServicesLocked not found; "
                    + "access control will deny all app binds.");
                return;
            }

            hook(target, new AbsHook() {
                @Override
                public void after() {
                    try {
                        Object raw = getResult();
                        if (!(raw instanceof Map)) {
                            logE(TAG, "getCommonServicesLocked returned no map; "
                                + "services not exposed.");
                            return;
                        }

                        Method target = (Method) getExecutable();
                        Map<Object, Object> filtered = newResultMap(target);
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                            filtered.put(entry.getKey(), entry.getValue());
                        }
                        filtered.remove(AccessPolicy.LEGACY_SERVICE);
                        filtered.remove(AccessPolicy.V4_SERVICE);

                        Integer uid = sBindUid.get();
                        if (uid != null && !isIsolatedBind(target)) {
                            AccessPolicy.Level level = AccessPolicy.levelForUid(uid);
                            SuperLyricService legacy = sSuperLyricService;
                            SuperLyricV4Service v4 = sSuperLyricV4Service;

                            if (level.allowsLegacy() && legacy != null) {
                                filtered.put(AccessPolicy.LEGACY_SERVICE, legacyEndpoint.binder);
                            }
                            if (level.allowsV4() && v4 != null) {
                                filtered.put(AccessPolicy.V4_SERVICE, v4Endpoint.binder);
                            }

                            logD(TAG, "Bind services for uid " + uid + ": " + level);
                        } else if (uid == null && !sUidMissingLogged) {
                            sUidMissingLogged = true;
                            logW(TAG, "attachApplicationLocked uid was missing when getCommonServicesLocked ran; "
                                + "services are not exposed. Further occurrences are not logged.");
                        }

                        setResult(filtered);
                    } catch (Throwable t) {
                        logE(TAG, "Failed to filter common services; services not exposed.", t);
                    }
                }

                private boolean isIsolatedBind(Method method) {
                    if (method.getParameterTypes().length == 0) return false;
                    try {
                        Object value = getArg(0);
                        return !(value instanceof Boolean) || (Boolean) value;
                    } catch (Throwable t) {
                        return true;
                    }
                }
            });
            logI(TAG, "Hooked getCommonServicesLocked.");
        } catch (Throwable t) {
            logE(TAG, "Failed to hook getCommonServicesLocked; access control will fail closed.", t);
        }
    }

    private void hookAppDiedLocked() {
        // Android 11-13 为四参签名；14 起多了 reason，部分版本再加两个 int。
        Method appDiedLockedMethod = findMethodIfExists("com.android.server.am.ActivityManagerService",
            "appDiedLocked",
            "com.android.server.am.ProcessRecord" /* app */, int.class /* pid */, "android.app.IApplicationThread" /* thread */,
            boolean.class /* fromBinderDied */
        );
        if (appDiedLockedMethod == null) {
            appDiedLockedMethod = findMethodIfExists("com.android.server.am.ActivityManagerService",
                "appDiedLocked",
                "com.android.server.am.ProcessRecord" /* app */, int.class /* pid */, "android.app.IApplicationThread" /* thread */,
                boolean.class /* fromBinderDied */, String.class /* reason */
            );
        }
        if (appDiedLockedMethod == null) {
            appDiedLockedMethod = findMethodIfExists(
                "com.android.server.am.ActivityManagerService",
                "appDiedLocked",
                "com.android.server.am.ProcessRecord" /* app */, int.class /* pid */, "android.app.IApplicationThread" /* thread */,
                boolean.class /* fromBinderDied */, String.class /* reason */, int.class, int.class
            );
        }

        if (appDiedLockedMethod == null) {
            logE(TAG, "ActivityManagerService#appDiedLocked() not found; process-death reclaim disabled.");
            return;
        }

        hook(appDiedLockedMethod,
            new AbsHook() {
                @Override
                public void after() {
                    try {
                        if (sRuntime == null) return;

                        Object app = getArg(0);
                        ApplicationInfo info = (ApplicationInfo) getField(app, "info");
                        if (info == null) return;

                        int pid = (int) getArg(1);

                        // 不按 ProcessRecord#mKilled 过滤：LMK 不经过 AMS 写该字段。
                        // 是否属于当前播放进程由 LyricRuntime#onProcessDied 的两级守卫判断。
                        Object procLock = getField(getThisObject(), "mProcLock");
                        if (procLock == null) {
                            if (!sProcLockMissingLogged) {
                                sProcLockMissingLogged = true;
                                logW(TAG, "AMS#mProcLock not found; ProcessRecord fields are read "
                                    + "unsynchronized. Process-death reclaim still works.");
                            }
                            sRuntime.onProcessDied(info.packageName, pid);
                            return;
                        }

                        synchronized (procLock) {
                            sRuntime.onProcessDied(info.packageName, pid);
                        }
                    } catch (Throwable t) {
                        // 进程死亡回调同样不能把异常抛回 AMS；一次失败最多漏掉一次回收。
                        logE(TAG, "Failed to handle appDiedLocked for super lyric.", t);
                    }
                }
            }
        );
    }

    /** HookTool 校验返回值必须是声明类型的实例；AMS 可能声明为 ArrayMap。 */
    private static Map<Object, Object> newResultMap(Method method) {
        Class<?> returnType = method.getReturnType();
        Map<Object, Object> result;
        if (returnType.isAssignableFrom(ArrayMap.class)) {
            result = new ArrayMap<>();
        } else if (returnType.isAssignableFrom(HashMap.class)) {
            result = new HashMap<>();
        } else {
            throw new IllegalStateException("Unsupported services map type: "
                + returnType.getName());
        }
        if (!returnType.isInstance(result)) {
            throw new IllegalStateException("Cannot create services map for "
                + returnType.getName());
        }
        return result;
    }

    private Class<?> findAmsClass() {
        Class<?> amsClass = CoreTool.findClass("com.android.server.am.ActivityManagerService");
        if (amsClass == null) {
            logE(TAG, "ActivityManagerService class not found; access control disabled.");
        }
        return amsClass;
    }
}