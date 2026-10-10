package com.hchen.superlyric.core;

import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Handler;
import android.text.TextUtils;
import com.hchen.hooktool.log.XposedLog;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SystemPlayStateListenerTest {
    private static final String QQ = "com.tencent.qqmusic";
    private static final String NETEASE = "com.netease.cloudmusic";

    private static void set(Object listener, String name, Object value) throws Exception {
        Field field = SystemPlayStateListener.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(listener, value);
    }

    @Test public void freshControllerWrappersDoNotRegisterCallbacksDuringStateQuery() throws Exception {
        try (MockedStatic<Binder> binder = mockStatic(Binder.class);
             MockedStatic<TextUtils> text = mockStatic(TextUtils.class);
             MockedStatic<XposedLog> log = mockStatic(XposedLog.class);
             MockedStatic<SuperLyricService> publishers = mockStatic(SuperLyricService.class)) {
            text.when(() -> TextUtils.equals(any(), any())).thenAnswer(call -> Objects.equals(call.getArgument(0), call.getArgument(1)));
            publishers.when(() -> SuperLyricService.isPublisher(QQ)).thenReturn(true);
            SystemPlayStateListener listener = mock(SystemPlayStateListener.class, CALLS_REAL_METHODS);
            SuperLyricService service = mock(SuperLyricService.class);
            MediaSessionManager manager = mock(MediaSessionManager.class);
            set(listener, "mService", service);
            set(listener, "mMediaSessionManager", manager);
            set(listener, "mMainHandler", mock(Handler.class));
            set(listener, "mCallbacks", new ConcurrentHashMap<>());
            AtomicInteger queries = new AtomicInteger();
            AtomicInteger registrations = new AtomicInteger();
            PlaybackState state = mock(PlaybackState.class);
            when(state.getState()).thenReturn(PlaybackState.STATE_PLAYING);
            when(manager.getActiveSessions(null)).thenAnswer(call -> {
                // Android creates new wrappers for the same session on each query.
                // Bound a broken implementation's recursion so the test itself cannot overflow.
                if (queries.incrementAndGet() > 3) throw new IllegalStateException("Recursive session query");
                MediaController fresh = mock(MediaController.class);
                when(fresh.getPackageName()).thenReturn(QQ);
                when(fresh.getPlaybackState()).thenReturn(state);
                doAnswer(registration -> { registrations.incrementAndGet(); return null; })
                    .when(fresh).registerCallback(any(), any());
                return List.of(fresh);
            });
            listener.syncCurrentStateForPackage(QQ);
            assertEquals("State synchronization must not recursively query sessions", 1, queries.get());
            assertEquals("A snapshot query must not install callbacks", 0, registrations.get());
            verify(service).onSystemPlaybackPriorityChanged(QQ);
            verify(service, atLeastOnce()).onSystemPlaybackStateChanged(QQ, state);
        }
    }

    @Test public void systemPriorityWinsWhenBothPlayersReportPlaying() throws Exception {
        try (MockedStatic<Binder> binder = mockStatic(Binder.class);
             MockedStatic<TextUtils> text = mockStatic(TextUtils.class);
             MockedStatic<XposedLog> log = mockStatic(XposedLog.class);
             MockedStatic<SuperLyricService> publishers = mockStatic(SuperLyricService.class)) {
            text.when(() -> TextUtils.equals(any(), any())).thenAnswer(call -> Objects.equals(call.getArgument(0), call.getArgument(1)));
            publishers.when(() -> SuperLyricService.isPublisher(QQ)).thenReturn(true);
            publishers.when(() -> SuperLyricService.isPublisher(NETEASE)).thenReturn(true);
            SystemPlayStateListener listener = mock(SystemPlayStateListener.class, CALLS_REAL_METHODS);
            SuperLyricService service = mock(SuperLyricService.class);
            MediaSessionManager manager = mock(MediaSessionManager.class);
            set(listener, "mService", service);
            set(listener, "mMediaSessionManager", manager);
            MediaController qq = mock(MediaController.class), netease = mock(MediaController.class);
            when(qq.getPackageName()).thenReturn(QQ);
            when(netease.getPackageName()).thenReturn(NETEASE);
            PlaybackState state = mock(PlaybackState.class);
            when(state.getState()).thenReturn(PlaybackState.STATE_PLAYING);
            when(qq.getPlaybackState()).thenReturn(state);
            when(netease.getPlaybackState()).thenReturn(state);
            when(manager.getActiveSessions(null)).thenReturn(List.of(netease, qq));
            listener.syncCurrentStateForPackage(QQ);
            verify(service).onSystemPlaybackPriorityChanged(NETEASE);
            verify(service, never()).onSystemPlaybackPriorityChanged(QQ);
        }
    }
}
