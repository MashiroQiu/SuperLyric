package com.hchen.superlyric.reload;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterfaceWrapper;

/** Prevent old callback Stubs from being pinned by queued remote broadcasts during handover. */
public final class CallbackEndpoints {
    private static final IdentityHashMap<Binder, Binder> endpoints = new IdentityHashMap<>();
    private static final ConcurrentHashMap<Binder, Binder> delegates = new ConcurrentHashMap<>();
    private static volatile boolean retired;
    private CallbackEndpoints() {}

    public static synchronized IBinder wrap(Binder stub) {
        Binder endpoint = endpoints.get(stub);
        if (endpoint == null) {
            endpoint = new Binder();
            endpoint.attachInterface(null, stub.getInterfaceDescriptor());
            endpoints.put(stub, endpoint);
            if (!retired) delegates.put(endpoint, stub);
        }
        return endpoint;
    }

    public static void install(XposedInterfaceWrapper module) {
        try {
            module.hook(Binder.class.getDeclaredMethod("onTransact", int.class, Parcel.class, Parcel.class, int.class))
                .setId("superlyric.callback.endpoints")
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    Binder target = delegates.get(chain.getThisObject());
                    if (target == null) return chain.proceed();
                    return target.transact((int) chain.getArg(0), (Parcel) chain.getArg(1),
                        (Parcel) chain.getArg(2), (int) chain.getArg(3));
                });
            for (String name : new String[]{
                "io.github.proify.lyricon.provider.IProviderBinder$Stub",
                "io.github.proify.lyricon.provider.IProviderService$Stub"
            }) {
                Class<?> stub = Class.forName(name);
                module.hook(stub.getDeclaredMethod("asBinder")).intercept(chain -> wrap((Binder) chain.getThisObject()));
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot install reload-safe callback endpoints", error);
        }
    }

    public static synchronized void retire() {
        retired = true;
        delegates.clear();
        endpoints.clear();
    }
}
