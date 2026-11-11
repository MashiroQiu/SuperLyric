package com.hchen.superlyric.reload;

import android.os.IBinder;
import android.os.Parcel;
import io.github.libxposed.api.XposedInterfaceWrapper;

/** Supplies the new service to an already-running process whose AMS cache predates v4. */
public final class EndpointDiscovery {
    public static final int DISCOVER_V4 = 0x5f535634;
    private EndpointDiscovery() {}
    public static void install(XposedInterfaceWrapper module) {
        try {
            java.lang.reflect.Method get = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
            module.hook(get).intercept(chain -> {
                Object original = chain.proceed();
                if (original != null || !"super_lyric.v4".equals(chain.getArg(0))) return original;
                IBinder legacy = (IBinder) get.invoke(null, "super_lyric");
                if (legacy == null) return null;
                Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    if (!legacy.transact(DISCOVER_V4, data, reply, 0)) return null;
                    reply.readException();
                    return reply.readStrongBinder();
                } finally { data.recycle(); reply.recycle(); }
            });
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Service discovery hook failed", error); }
    }
}
