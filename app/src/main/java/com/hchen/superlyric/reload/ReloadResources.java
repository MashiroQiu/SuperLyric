package com.hchen.superlyric.reload;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Synchronous retirement: returning from this method means the resource has stopped. */
public final class ReloadResources {
    public static volatile boolean retiring;
    private ReloadResources() {}

    public static <T> T onMain(Callable<T> action) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) return action.call();
            FutureTask<T> task = new FutureTask<>(action);
            Handler handler = new Handler(Looper.getMainLooper());
            if (!handler.post(task)) throw new IllegalStateException("Main looper is stopping");
            try {
                return task.get(8, TimeUnit.SECONDS);
            } catch (Exception error) {
                handler.removeCallbacks(task);
                task.cancel(false);
                throw error;
            }
        } catch (Exception error) {
            throw new IllegalStateException("Hot reload main-thread cleanup failed", error);
        }
    }

    public static void stop(ExecutorService executor) {
        if (executor == null) return;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(8, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Hot reload worker did not terminate");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retiring hot reload worker", error);
        }
    }
}
