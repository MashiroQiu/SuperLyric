package com.hchen.superlyric.reload;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import okio.AsyncTimeout;

/**
 * Retire only this generation's idle Okio watchdog through its normal sentinel exit path. Every
 * network call must already be cancelled and joined. A shared host Okio or a nonempty timeout
 * queue is never altered. Supports the pinned 3.15 linked-list and 3.18 priority-queue layouts.
 */
public final class OwnedOkio {
    private OwnedOkio() {}
    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    public static void retire(ClassLoader owner) {
        if (AsyncTimeout.class.getClassLoader() != owner) return;
        List<Thread> watchdogs = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getClass().getClassLoader() == owner &&
                thread.getClass().getName().equals("okio.AsyncTimeout$Watchdog")) watchdogs.add(thread);
        }
        if (watchdogs.isEmpty()) return;
        try {
            Class<?> type = AsyncTimeout.class;
            ReentrantLock lock = (ReentrantLock) field(type, "lock").get(null);
            lock.lock();
            try {
                Object first;
                AsyncTimeout sentinel;
                try {
                    Object queue = field(type, "queue").get(null);
                    Method method = queue.getClass().getDeclaredMethod("first");
                    method.setAccessible(true);
                    first = method.invoke(queue);
                    sentinel = (AsyncTimeout) field(type, "idleSentinel").get(null);
                } catch (NoSuchFieldException oldLayout) {
                    sentinel = (AsyncTimeout) field(type, "head").get(null);
                    first = sentinel == null ? null : field(type, "next").get(sentinel);
                }
                if (first != null) throw new IllegalStateException("Okio still owns active timeouts");
                if (sentinel != null) {
                    // The watchdog already uses this identity as its exit marker. Scheduling that
                    // marker as an immediately due node wakes it without a 60-second idle wait.
                    // The retiring generation will never schedule another timeout after this.
                    sentinel.timeout(1, TimeUnit.NANOSECONDS);
                    sentinel.deadlineNanoTime(System.nanoTime());
                    sentinel.enter();
                }
            } finally { lock.unlock(); }
            for (Thread thread : watchdogs) {
                thread.join(8000);
                if (thread.isAlive()) throw new IllegalStateException("Okio watchdog did not terminate");
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot retire this Okio watchdog layout", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted retiring Okio watchdog", error);
        }
    }
}
