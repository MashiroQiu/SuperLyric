package com.hchen.superlyric.reload;

import java.util.concurrent.ExecutorService;

/** The pinned SuperLyricApi 4.0 background executor has no public retirement method. */
public final class SdkWorkers {
    private SdkWorkers() {}
    public static void close() {
        try {
            Class<?> background = Class.forName("com.hchen.superlyricapi.internal.Background");
            java.lang.reflect.Field field = background.getDeclaredField("executor");
            field.setAccessible(true);
            ReloadResources.stop((ExecutorService) field.get(null));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("SDK worker retirement failed", error); }
    }
    public static void resetTransport() {
        try {
            Class<?> hub = Class.forName("com.hchen.superlyricapi.internal.BinderHub");
            hub.getDeclaredMethod("reset").invoke(hub.getDeclaredField("INSTANCE").get(null));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("SDK transport reset failed", error); }
    }
}
