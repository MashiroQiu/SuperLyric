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
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hchen.hooktool.log.XposedLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统级播放状态监听器
 *
 * <p>在 system_server 内部监听全局活动的 MediaSession，把系统级播放状态与元数据变更同步至
 * {@link LyricRuntime}。回调经专用的 handler 线程派发，不再占用 system_server 主线程；
 * 运行时方法仍不得在该线程上阻塞。
 *
 */
public final class SystemPlayStateListener {
    private static final String TAG = "SystemPlayStateListener";

    @NonNull
    private final LyricRuntime mRuntime;
    @NonNull
    private final MediaSessionManager mMediaSessionManager;
    @NonNull
    private final ConcurrentHashMap<MediaSession.Token, MediaControllerCallback> mCallbacks = new ConcurrentHashMap<>();
    private volatile Map<String, MediaSession.Token> preferredTokens = new HashMap<>();
    @NonNull
    private final Handler mCallbackHandler;
    private final HandlerThread callbackThread;
    private volatile boolean closed;

    @NonNull
    private final MediaSessionManager.OnActiveSessionsChangedListener mListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
            if (closed || controllers == null) return;

            long token = Binder.clearCallingIdentity();
            try {
                Map<String, MediaSession.Token> preferred = new HashMap<>();
                Set<String> playing = new HashSet<>();
                Set<MediaSession.Token> activeTokens = new HashSet<>();
                for (MediaController controller : controllers) {
                    String pkg = controller.getPackageName();
                    activeTokens.add(controller.getSessionToken());
                    PlaybackState state = controller.getPlaybackState();
                    boolean isPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
                    if (!preferred.containsKey(pkg) || (isPlaying && !playing.contains(pkg))) {
                        preferred.put(pkg, controller.getSessionToken());
                    }
                    if (isPlaying) playing.add(pkg);
                }
                synchronized (mCallbacks) { preferredTokens = preferred; }
                for (Map.Entry<MediaSession.Token, MediaControllerCallback> entry : new ArrayList<>(mCallbacks.entrySet())) {
                    if (!activeTokens.contains(entry.getKey()) && mCallbacks.remove(entry.getKey(), entry.getValue())) {
                        entry.getValue().mController.unregisterCallback(entry.getValue());
                    }
                }
                // Android orders sessions by priority; apply the highest-priority initial snapshot last.
                for (int i = controllers.size() - 1; i >= 0; i--) {
                    registerMediaControllerCallback(controllers.get(i));
                }
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    public SystemPlayStateListener(@NonNull Context context, @NonNull LyricRuntime runtime) {
        this(runtime, (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE), startThread());
    }

    private SystemPlayStateListener(LyricRuntime runtime, MediaSessionManager manager, HandlerThread thread) {
        this(runtime, manager, new Handler(thread.getLooper()), thread);
    }

    SystemPlayStateListener(LyricRuntime runtime, MediaSessionManager manager, Handler handler, HandlerThread thread) {
        mRuntime = runtime;
        mMediaSessionManager = manager;
        mCallbackHandler = handler;
        callbackThread = thread;
    }

    private static HandlerThread startThread() {
        HandlerThread thread = new HandlerThread("SuperLyric-MediaSession");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public void closeForReload() {
        closed = true;
        mMediaSessionManager.removeOnActiveSessionsChangedListener(mListener);
        for (MediaControllerCallback callback : mCallbacks.values()) {
            callback.mController.unregisterCallback(callback);
        }
        mCallbacks.clear();
        preferredTokens = new HashMap<>();
        mCallbackHandler.removeCallbacksAndMessages(null);
        callbackThread.quitSafely();
        try {
            callbackThread.join(8000);
            if (callbackThread.isAlive()) throw new IllegalStateException("Media callback thread still running");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(error);
        }
    }

    /**
     * 注册全局 MediaSession 监听
     *
     * @return 是否成功注册到系统 MediaSessionManager；为 false 时调用方不得记录初始化成功
     */
    public boolean register() {
        long token = Binder.clearCallingIdentity();
        try {
            mListener.onActiveSessionsChanged(mMediaSessionManager.getActiveSessions(null));
            mMediaSessionManager.addOnActiveSessionsChangedListener(mListener, null, mCallbackHandler);
            return true;
        } catch (Throwable t) {
            XposedLog.logE(TAG, "Failed to register SystemPlayStateListener", t);
            return false;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * 主动向指定包名的活跃 MediaSession 查询并同步当前播放状态与元数据
     */
    public void syncCurrentStateForPackage(@NonNull String packageName) {
        // 与 onPlaybackStateChanged/onMetadataChanged 使用同一判定：否则按包禁用只挡住
        // 后续回调，主动 sync 仍会把状态喂给服务端。
        if (mRuntime.isSystemPlayStateListenerDisabled(packageName)) return;
        long token = Binder.clearCallingIdentity();
        try {
            MediaController preferred = null;
            for (MediaController controller : mMediaSessionManager.getActiveSessions(null)) {
                if (!packageName.equals(controller.getPackageName())) continue;
                if (preferred == null) preferred = controller;
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    preferred = controller;
                    break;
                }
            }
            if (preferred == null) return;
            select(preferred);
            MediaControllerCallback callback = mCallbacks.get(preferred.getSessionToken());
            if (callback != null) mCallbackHandler.post(callback::syncLatest);
            else registerMediaControllerCallback(preferred);
        } catch (Throwable t) {
            XposedLog.logW(TAG, "Failed to sync state for package " + packageName, t);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void registerMediaControllerCallback(@NonNull MediaController controller) {
        if (closed) return;
        long token = Binder.clearCallingIdentity();
        try {
            MediaSession.Token sessionToken = controller.getSessionToken();
            if (mCallbacks.containsKey(sessionToken)) return;
            MediaControllerCallback callback = new MediaControllerCallback(controller);
            if (mCallbacks.putIfAbsent(sessionToken, callback) != null) return;
            try {
                controller.registerCallback(callback, mCallbackHandler);
            } catch (Throwable error) {
                mCallbacks.remove(sessionToken, callback);
                throw error;
            }
            if (closed || mCallbacks.get(sessionToken) != callback) {
                controller.unregisterCallback(callback);
                mCallbacks.remove(sessionToken, callback);
                return;
            }
            mCallbackHandler.post(callback::syncLatest);
        } catch (Throwable t) {
            XposedLog.logW(TAG, "Failed to register callback for controller: "
                + controller.getPackageName(), t);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void select(MediaController controller) {
        synchronized (mCallbacks) {
            Map<String, MediaSession.Token> next = new HashMap<>(preferredTokens);
            next.put(controller.getPackageName(), controller.getSessionToken());
            preferredTokens = next;
        }
    }

    private class MediaControllerCallback extends MediaController.Callback {
        @NonNull
        private final MediaController mController;
        private volatile PlaybackState lastState;

        private MediaControllerCallback(@NonNull MediaController controller) {
            mController = controller;
            lastState = controller.getPlaybackState();
        }

        private boolean isCurrent() {
            return !closed && mCallbacks.get(mController.getSessionToken()) == this;
        }

        private boolean isPreferred() {
            return mController.getSessionToken().equals(preferredTokens.get(mController.getPackageName()));
        }

        private void syncLatest() {
            if (!isCurrent() || !isPreferred()) return;
            try {
                MediaMetadata metadata = mController.getMetadata();
                if (metadata != null) onMetadataChanged(metadata);
                PlaybackState state = mController.getPlaybackState();
                if (state != null) onPlaybackStateChanged(state);
            } catch (Throwable error) {
                XposedLog.logW(TAG, "Failed to sync state for " + mController.getPackageName(), error);
            }
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            super.onPlaybackStateChanged(state);
            if (state == null || !isCurrent()) return;
            String pkg = mController.getPackageName();
            PlaybackState previous = lastState;
            lastState = state;
            if (!isPreferred() && state.getState() == PlaybackState.STATE_PLAYING) {
                MediaSession.Token selectedToken = preferredTokens.get(pkg);
                MediaControllerCallback selected = selectedToken != null ? mCallbacks.get(selectedToken) : null;
                PlaybackState selectedState = selected != null ? selected.lastState : null;
                if (previous == null || previous.getState() != PlaybackState.STATE_PLAYING
                    || selectedState == null || state.getLastPositionUpdateTime() > selectedState.getLastPositionUpdateTime()) {
                    select(mController);
                    onMetadataChanged(mController.getMetadata());
                }
            }
            if (!isPreferred()) return;
            if (mRuntime.isSystemPlayStateListenerDisabled(pkg)) {
                return;
            }

            if (mRuntime.isPublisher(pkg)) {
                mRuntime.onSystemPlaybackStateChanged(pkg, state);
            }
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            if (metadata == null || !isCurrent() || !isPreferred()) return;
            String pkg = mController.getPackageName();
            if (mRuntime.isSystemPlayStateListenerDisabled(pkg)) {
                return;
            }

            if (mRuntime.isPublisher(pkg)) {
                mRuntime.onSystemMetadataChanged(pkg, metadata);
            }
        }

        /**
         * 会话被销毁
         *
         * <p>宿主释放会话而进程仍在时，这是唯一能到达本模块的通知：播放状态不会再变，
         * {@code onActiveSessionsChanged} 只摘掉回调，不会报告该包停止。缺少这条通知时，
         * 界面会停在最后一行歌词上。
         */
        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
            if (!isCurrent()) return;
            mCallbacks.remove(mController.getSessionToken(), this);
            mController.unregisterCallback(this);
            if (!isPreferred()) return;
            String pkg = mController.getPackageName();
            if (mRuntime.isSystemPlayStateListenerDisabled(pkg)) {
                return;
            }

            if (mRuntime.isPublisher(pkg)) {
                mRuntime.onSystemSessionDestroyed(pkg);
            }
        }
    }
}
