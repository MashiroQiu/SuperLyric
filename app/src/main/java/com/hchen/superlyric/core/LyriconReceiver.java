/*
 * This file is part of SuperLyric.
 * Licensed under the GNU General Public License, version 3.
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hchen.hooktool.AbsModule;
import com.hchen.processor.HookThis;
import com.hchen.superlyric.lyricon.LyriconBridge;

import io.github.libxposed.api.XposedModuleInterface;

/** Bridges the arbitrated SuperLyric stream in the long-lived System UI process. */
@HookThis(targetPackage = "com.android.systemui")
public final class LyriconReceiver extends AbsModule {
    @Override
    protected void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
    }

    @Override
    protected void onApplicationCreated(@NonNull Context context) {
        LyriconBridge.start(context);
    }
}
