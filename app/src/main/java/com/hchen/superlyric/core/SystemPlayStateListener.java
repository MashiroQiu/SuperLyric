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
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hchen.hooktool.log.XposedLog;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统级播放状态监听器。
 * <p>
 * 在 system_server 内部监听全局活动的 MediaSession，将系统级播放状态与元数据变更实时同步至 SuperLyricService。
 *
 * @author 焕晨HChen
 */
public final class SystemPlayStateListener {
    private static final String TAG = "SystemPlayStateListener";
    private volatile boolean mClosed;

    @NonNull
    private final Context mContext;
    @NonNull
    private final SuperLyricService mService;
    @NonNull
    private final MediaSessionManager mMediaSessionManager;
    @NonNull
    private final ConcurrentHashMap<MediaController, MediaControllerCallback> mCallbacks = new ConcurrentHashMap<>();
    @NonNull
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @NonNull
    private final MediaSessionManager.OnActiveSessionsChangedListener mListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
            if (mClosed) return;

            long token = Binder.clearCallingIdentity();
            try {
                mCallbacks.forEach(MediaController::unregisterCallback);
                mCallbacks.clear();
                List<MediaController> current = controllers == null ? java.util.Collections.emptyList() : controllers;
                refreshPlaybackPriority(current);
                for (MediaController controller : current) {
                    registerMediaControllerCallback(controller);
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    public SystemPlayStateListener(@NonNull Context context, @NonNull SuperLyricService service) {
        mContext = context;
        mService = service;
        mMediaSessionManager = (MediaSessionManager) mContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public void close() {
        mClosed = true;
        com.hchen.superlyric.reload.ReloadResources.onMain(() -> {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mListener);
            mCallbacks.forEach(MediaController::unregisterCallback);
            mCallbacks.clear();
            mMainHandler.removeCallbacksAndMessages(null);
            return null;
        });
    }

    public void register() {
        long token = Binder.clearCallingIdentity();
        try {
            List<MediaController> controllers = mMediaSessionManager.getActiveSessions(null);
            refreshPlaybackPriority(controllers);
            for (MediaController controller : controllers) {
                registerMediaControllerCallback(controller);
            }
            mMediaSessionManager.addOnActiveSessionsChangedListener(mListener, null);
        } catch (Throwable t) {
            XposedLog.logW(TAG, "Failed to register SystemPlayStateListener: " + t.getMessage());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * 主动向指定包名的活跃 MediaSession 查询并同步当前播放状态与元数据。
     */
    public void syncCurrentStateForPackage(@NonNull String packageName) {
        if (mClosed || SuperLyricService.isSystemPlayStateListenerDisabled(packageName)) return;
        long token = Binder.clearCallingIdentity();
        try {
            List<MediaController> controllers = mMediaSessionManager.getActiveSessions(null);
            refreshPlaybackPriority(controllers);
            for (MediaController controller : controllers) {
                if (TextUtils.equals(controller.getPackageName(), packageName)) {
                    // Queries create fresh controller wrappers. Registering here would call
                    // the initial playback callback, which re-enters this query indefinitely.
                    // Callback registration belongs exclusively to session-list changes.
                    PlaybackState state = controller.getPlaybackState();
                    if (state != null) {
                        mService.onSystemPlaybackStateChanged(packageName, state);
                    }
                    MediaMetadata metadata = controller.getMetadata();
                    if (metadata != null) {
                        mService.onSystemMetadataChanged(packageName, metadata);
                    }
                    return;
                }
            }

        } catch (Throwable t) {
            XposedLog.logD(TAG, "Failed to sync state for package " + packageName + ": " + t.getMessage());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void refreshPlaybackPriority(@NonNull List<MediaController> controllers) {
        // getActiveSessions() is ordered by system priority. Never let iteration order in
        // mCallbacks or an old player's repeated PLAYING updates choose the lyric source.
        for (MediaController controller : controllers) {
            String pkg = controller.getPackageName();
            if (!SuperLyricService.isPublisher(pkg) || SuperLyricService.isSystemPlayStateListenerDisabled(pkg)) continue;
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                mService.onSystemPlaybackPriorityChanged(pkg);
                mService.onSystemPlaybackStateChanged(pkg, state);
                return;
            }
        }
        mService.onSystemPlaybackPriorityChanged(null);
    }

    private void refreshPlaybackPriority() {
        long token = Binder.clearCallingIdentity();
        try {
            refreshPlaybackPriority(mMediaSessionManager.getActiveSessions(null));
        } catch (Throwable t) {
            XposedLog.logD(TAG, "Failed to refresh playback priority: " + t.getMessage());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void registerMediaControllerCallback(@NonNull MediaController controller) {
        long token = Binder.clearCallingIdentity();
        try {
            MediaControllerCallback callback = mCallbacks.get(controller);
            if (callback != null) {
                controller.unregisterCallback(callback);
                mCallbacks.remove(controller);
            }

            callback = new MediaControllerCallback(controller);
            controller.registerCallback(callback, mMainHandler);
            mCallbacks.put(controller, callback);

            // 主动同步当前初始状态
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                callback.onPlaybackStateChanged(state);
            }
            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                callback.onMetadataChanged(metadata);
            }
        } catch (Throwable t) {
            XposedLog.logD(TAG, "Failed to register callback for controller: " + controller.getPackageName() + ", " + t.getMessage());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private class MediaControllerCallback extends MediaController.Callback {
        @NonNull
        private final MediaController mController;

        private MediaControllerCallback(@NonNull MediaController controller) {
            mController = controller;
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            super.onPlaybackStateChanged(state);
            if (mClosed || state == null) return;
            String pkg = mController.getPackageName();
            if (SuperLyricService.isSystemPlayStateListenerDisabled(pkg)) {
                return;
            }

            if (SuperLyricService.isPublisher(pkg)) {
                // Callback arguments can already be stale. Read the highest-priority session
                // for this package so an auxiliary/old session cannot pause its live player.
                syncCurrentStateForPackage(pkg);
            }
        }

        @Override
        public void onSessionDestroyed() {
            if (mClosed) return;
            mCallbacks.remove(mController);
            refreshPlaybackPriority();
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (mClosed || metadata == null) return;
            String pkg = mController.getPackageName();
            if (SuperLyricService.isSystemPlayStateListenerDisabled(pkg)) {
                return;
            }

            if (SuperLyricService.isPublisher(pkg)) {
                mService.onSystemMetadataChanged(pkg, metadata);
            }
        }
    }
}
