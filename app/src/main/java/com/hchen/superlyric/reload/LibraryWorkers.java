package com.hchen.superlyric.reload;

import kotlinx.coroutines.Dispatchers;
import okhttp3.internal.concurrent.TaskRunner;

/** Shut down library workers only when they belong to this isolated module generation. */
public final class LibraryWorkers {
    private LibraryWorkers() {}
    public static void close() {
        ClassLoader owner = LibraryWorkers.class.getClassLoader();
        if (TaskRunner.class.getClassLoader() == owner) {
            TaskRunner.INSTANCE.cancelAll();
            if (TaskRunner.INSTANCE.getBackend() instanceof TaskRunner.RealBackend backend) {
                ReloadResources.stop(backend.getExecutor());
            }
        }
        OwnedOkio.retire(owner);
        if (Dispatchers.class.getClassLoader() == owner) Dispatchers.INSTANCE.shutdown();
    }
}
