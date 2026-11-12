package com.hchen.superlyric.core;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemPlayStateListenerTest {
    private static class Harness {
        final LyricRuntime runtime = mock(LyricRuntime.class);
        final MediaSessionManager manager = mock(MediaSessionManager.class);
        final Handler handler = mock(Handler.class);
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final Map<MediaController, MediaController.Callback> callbacks = new IdentityHashMap<>();
        List<MediaController> active = new ArrayList<>();
        MediaSessionManager.OnActiveSessionsChangedListener changes;
        final SystemPlayStateListener listener;

        Harness() {
            when(runtime.isPublisher(anyString())).thenReturn(true);
            when(manager.getActiveSessions(isNull())).thenAnswer(call -> active);
            doAnswer(call -> { tasks.add(call.getArgument(0)); return true; })
                .when(handler).post(any(Runnable.class));
            doAnswer(call -> { changes = call.getArgument(0); return null; })
                .when(manager).addOnActiveSessionsChangedListener(any(), isNull(), same(handler));
            listener = new SystemPlayStateListener(runtime, manager, handler, mock(HandlerThread.class));
        }

        MediaController controller(String pkg, int status) {
            return controller(pkg, status, mock(MediaSession.Token.class));
        }

        MediaController controller(String pkg, int status, MediaSession.Token token) {
            MediaController controller = mock(MediaController.class);
            PlaybackState state = mock(PlaybackState.class);
            when(state.getState()).thenReturn(status);
            when(controller.getPlaybackState()).thenReturn(state);
            when(controller.getMetadata()).thenReturn(mock(MediaMetadata.class));
            when(controller.getSessionToken()).thenReturn(token);
            when(controller.getPackageName()).thenReturn(pkg);
            doAnswer(call -> { callbacks.put(controller, call.getArgument(0)); return null; })
                .when(controller).registerCallback(any(), same(handler));
            return controller;
        }

        void start(MediaController... controllers) {
            active = List.of(controllers);
            assertTrue(listener.register());
            drain();
        }

        void change(MediaController... controllers) {
            active = List.of(controllers);
            changes.onActiveSessionsChanged(active);
            drain();
        }

        void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    }

    @Test void freshControllerObjectsWithTheSameTokenDoNotReplayOldSongs() {
        Harness h = new Harness();
        MediaController first = h.controller("music", PlaybackState.STATE_PLAYING);
        h.start(first);
        clearInvocations(h.runtime);
        MediaController alias = h.controller("music", PlaybackState.STATE_PLAYING, first.getSessionToken());
        MediaController unrelated = h.controller("other", PlaybackState.STATE_PAUSED);
        h.change(alias, unrelated);
        verify(first, times(1)).registerCallback(any(), same(h.handler));
        verify(first, never()).unregisterCallback(any());
        verify(alias, never()).registerCallback(any(), any());
        verify(h.runtime, never()).onSystemMetadataChanged(eq("music"), any());
        verify(h.runtime, never()).onSystemPlaybackStateChanged(eq("music"), any());
    }

    @Test void removedSessionCallbacksCannotRestoreLyricsOrStopTheReplacement() {
        Harness h = new Harness();
        MediaController old = h.controller("music", PlaybackState.STATE_PLAYING);
        h.start(old);
        MediaController.Callback oldCallback = h.callbacks.get(old);
        h.change(h.controller("music", PlaybackState.STATE_PLAYING));
        clearInvocations(h.runtime);
        oldCallback.onMetadataChanged(old.getMetadata());
        oldCallback.onPlaybackStateChanged(old.getPlaybackState());
        oldCallback.onSessionDestroyed();
        verifyNoInteractions(h.runtime);
        verify(old).unregisterCallback(oldCallback);
    }

    @Test void pausedSessionMetadataCannotReplaceThePlayingSessionInTheSameApp() {
        Harness h = new Harness();
        MediaController paused = h.controller("music", PlaybackState.STATE_PAUSED);
        MediaController playing = h.controller("music", PlaybackState.STATE_PLAYING);
        h.start(paused, playing);
        verify(h.runtime, never()).onSystemMetadataChanged("music", paused.getMetadata());
        verify(h.runtime).onSystemMetadataChanged("music", playing.getMetadata());
        clearInvocations(h.runtime);
        h.callbacks.get(paused).onMetadataChanged(paused.getMetadata());
        h.listener.syncCurrentStateForPackage("music");
        h.drain();
        verify(h.runtime, never()).onSystemMetadataChanged("music", paused.getMetadata());
        verify(h.runtime).onSystemMetadataChanged("music", playing.getMetadata());
    }

    @Test void aRealPlayTransitionCanSelectThePreviouslyPausedSession() {
        Harness h = new Harness();
        MediaController paused = h.controller("music", PlaybackState.STATE_PAUSED);
        MediaController playing = h.controller("music", PlaybackState.STATE_PLAYING);
        h.start(playing, paused);
        clearInvocations(h.runtime);
        PlaybackState resumed = mock(PlaybackState.class);
        when(resumed.getState()).thenReturn(PlaybackState.STATE_PLAYING);
        h.callbacks.get(paused).onPlaybackStateChanged(resumed);
        verify(h.runtime).onSystemMetadataChanged("music", paused.getMetadata());
        verify(h.runtime).onSystemPlaybackStateChanged("music", resumed);
    }

    @Test void unchangedSessionListsDoNotAddSnapshotOrRegistrationWork() {
        Harness h = new Harness();
        MediaController current = h.controller("music", PlaybackState.STATE_PLAYING);
        h.start(current);
        clearInvocations(h.runtime);
        for (int i = 0; i < 100; i++) h.change(current);
        verifyNoInteractions(h.runtime);
        verify(current, times(1)).registerCallback(any(), same(h.handler));
        assertTrue(h.tasks.isEmpty());
    }
}
