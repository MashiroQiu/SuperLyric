package com.hchen.superlyric.core;

import android.media.session.PlaybackState;
import android.os.RemoteCallbackList;
import android.text.TextUtils;
import com.hchen.hooktool.log.XposedLog;
import com.hchen.superlyric.publisher.PlaybackTracker;
import com.hchen.superlyricapi.ISuperLyricReceiver;
import com.hchen.superlyricapi.SuperLyricData;
import com.hchen.superlyricapi.SuperLyricLine;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SuperLyricPlaybackSwitchTest {
    private static final String QQ = "com.tencent.qqmusic";
    private static final String NETEASE = "com.netease.cloudmusic";

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = SuperLyricService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = SuperLyricService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private PlaybackState playing() {
        PlaybackState state = mock(PlaybackState.class);
        when(state.getState()).thenReturn(PlaybackState.STATE_PLAYING);
        return state;
    }

    @Test public void backgroundPlayingCallbacksAndQueuedLyricsCannotUndoSwitch() throws Exception {
        try (MockedStatic<TextUtils> text = mockStatic(TextUtils.class);
             MockedStatic<android.os.SystemClock> clock = mockStatic(android.os.SystemClock.class);
             MockedStatic<XposedLog> log = mockStatic(XposedLog.class)) {
            text.when(() -> TextUtils.equals(any(), any())).thenAnswer(call -> Objects.equals(call.getArgument(0), call.getArgument(1)));
            text.when(() -> TextUtils.isEmpty(any())).thenAnswer(call -> call.getArgument(0) == null || ((CharSequence) call.getArgument(0)).length() == 0);
            SuperLyricService service = mock(SuperLyricService.class, CALLS_REAL_METHODS);
            set(service, "mSnapshotLock", new Object());
            set(service, "mSessions", new ConcurrentHashMap<>());
            PlaybackTracker tracker = mock(PlaybackTracker.class);
            set(service, "mSystemTracker", tracker);
            List<Runnable> queued = new ArrayList<>();
            ExecutorService executor = mock(ExecutorService.class);
            doAnswer(call -> { queued.add(call.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
            set(service, "mBroadcastExecutor", executor);
            ISuperLyricReceiver receiver = mock(ISuperLyricReceiver.class);
            RemoteCallbackList<ISuperLyricReceiver> callbacks = mock(RemoteCallbackList.class);
            when(callbacks.beginBroadcast()).thenReturn(1);
            when(callbacks.getBroadcastItem(0)).thenReturn(receiver);
            set(service, "mCallbacks", callbacks);
            ConcurrentHashMap<String, Set<Integer>> publishers = (ConcurrentHashMap<String, Set<Integer>>) get(null, "sPublisherPids");
            publishers.put(QQ, Set.of(1));
            publishers.put(NETEASE, Set.of(2));
            try {
                service.onSystemPlaybackPriorityChanged(QQ);
                service.onSystemPlaybackStateChanged(QQ, playing());
                Object sessions = get(service, "mSessions");
                Object qqSession = ((ConcurrentHashMap<?, ?>) sessions).get(QQ);
                Field lyric = qqSession.getClass().getDeclaredField("latestLyric");
                lyric.setAccessible(true);
                SuperLyricData qqLyrics = new SuperLyricData().setLyricId("qq-song")
                    .setLyric(new SuperLyricLine("QQ line", null, null, 0, 3000));
                lyric.set(qqSession, qqLyrics);
                service.onSystemPlaybackPriorityChanged(NETEASE);
                service.onSystemPlaybackStateChanged(NETEASE, playing());
                service.onSystemPlaybackPriorityChanged(QQ);
                service.onSystemPlaybackStateChanged(QQ, playing()); // Queues QQ's full snapshot.
                service.onSystemPlaybackPriorityChanged(NETEASE);
                service.onSystemPlaybackStateChanged(NETEASE, playing());
                service.onSystemPlaybackStateChanged(QQ, playing()); // Late repeat from the old player.
                assertEquals(NETEASE, get(service, "mActivePublisher"));
                queued.forEach(Runnable::run);
                verify(receiver, never()).onLyric(eq(QQ), any());
                service.onSystemPlaybackPriorityChanged(QQ);
                service.onSystemPlaybackStateChanged(QQ, playing()); // Switching back still works.
                assertEquals(QQ, get(service, "mActivePublisher"));
                assertSame(qqLyrics, service.getLatestLyric());
            } finally {
                publishers.remove(QQ);
                publishers.remove(NETEASE);
            }
        }
    }
}
