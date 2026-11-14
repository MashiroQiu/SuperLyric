package com.hchen.superlyric.reload;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedInterfaceWrapper;

/** Prevent old callback Stubs from being pinned by queued remote broadcasts during handover. */
public final class CallbackEndpoints {
    private static final WeakHashMap<Binder, Binder> endpoints = new WeakHashMap<>();
    private static final ConcurrentHashMap<Binder, Binder> delegates = new ConcurrentHashMap<>();
    private static final java.util.Set<java.util.Map.Entry<IBinder, IBinder.DeathRecipient>> deaths = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final WeakHashMap<Binder, Boolean> released = new WeakHashMap<>();
    private static final java.util.concurrent.locks.ReentrantReadWriteLock dispatchLock = new java.util.concurrent.locks.ReentrantReadWriteLock(true);
    private static Binder frameEndpoint;
    private static Binder legacyEndpoint;
    private static volatile boolean retired;
    private CallbackEndpoints() {}

    public static synchronized IBinder wrap(Binder stub) {
        Binder endpoint = endpoints.get(stub);
        if (endpoint == null) {
            String descriptor = stub.getInterfaceDescriptor();
            if ("com.hchen.superlyricapi.v4.ILyricFrameListener".equals(descriptor)) {
                if (frameEndpoint == null) frameEndpoint = carrier(descriptor);
                endpoint = frameEndpoint;
            } else if ("com.hchen.superlyricapi.ISuperLyricReceiver".equals(descriptor)) {
                if (legacyEndpoint == null) legacyEndpoint = carrier(descriptor);
                endpoint = legacyEndpoint;
            } else endpoint = carrier(descriptor);
            endpoints.put(stub, endpoint);
            if (!retired && !released.containsKey(stub)) delegates.put(endpoint, stub);
        }
        return endpoint;
    }

    private static Binder carrier(String descriptor) {
        Binder binder = new Binder();
        binder.attachInterface(null, descriptor);
        return binder;
    }

    public static synchronized void forget(Binder stub) {
        released.put(stub, true);
        Binder endpoint = endpoints.get(stub);
        if (endpoint != null) delegates.remove(endpoint, stub);
    }

    public static synchronized void unlinkRecorded(IBinder.DeathRecipient recipient) {
        for (java.util.Map.Entry<IBinder, IBinder.DeathRecipient> entry : deaths) {
            if (entry.getValue() == recipient) {
                entry.getKey().unlinkToDeath(recipient, 0);
                deaths.remove(entry);
            }
        }
    }

    public static synchronized void restore(java.util.Map<String, Object> state) {
        if (state == null) return;
        frameEndpoint = (Binder) state.get("frame");
        legacyEndpoint = (Binder) state.get("legacy");
    }

    // LSPosed must intercept BinderProxy death links to retire callbacks from the old module loader.
    @android.annotation.SuppressLint("PrivateApi")
    public static void install(XposedInterfaceWrapper module) {
        try {
            module.hook(Binder.class.getDeclaredMethod("onTransact", int.class, Parcel.class, Parcel.class, int.class))
                .setId("superlyric.callback.endpoints")
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept(chain -> {
                    if (retired || !delegates.containsKey(chain.getThisObject())) return chain.proceed();
                    dispatchLock.readLock().lock();
                    try {
                        Binder target = delegates.get(chain.getThisObject());
                        if (retired || target == null) return chain.proceed();
                        return target.transact((int) chain.getArg(0), (Parcel) chain.getArg(1),
                            (Parcel) chain.getArg(2), (int) chain.getArg(3));
                    } finally { dispatchLock.readLock().unlock(); }
                });
            Class<?> proxy = Class.forName("android.os.BinderProxy");
            module.hook(proxy.getDeclaredMethod("linkToDeath", IBinder.DeathRecipient.class, int.class))
                .intercept(chain -> {
                    Object result = chain.proceed();
                    IBinder.DeathRecipient recipient = (IBinder.DeathRecipient) chain.getArg(0);
                    if (recipient.getClass().getClassLoader() == CallbackEndpoints.class.getClassLoader()) {
                        IBinder binder = (IBinder) chain.getThisObject();
                        synchronized (CallbackEndpoints.class) {
                            if (retired) binder.unlinkToDeath(recipient, 0);
                            else deaths.add(new java.util.AbstractMap.SimpleImmutableEntry<>(binder, recipient));
                        }
                    }
                    return result;
                });
            module.hook(proxy.getDeclaredMethod("unlinkToDeath", IBinder.DeathRecipient.class, int.class))
                .intercept(chain -> {
                    Object result = chain.proceed();
                    deaths.remove(new java.util.AbstractMap.SimpleImmutableEntry<>((IBinder) chain.getThisObject(),
                        (IBinder.DeathRecipient) chain.getArg(0)));
                    return result;
                });
            for (String name : new String[]{
                "com.hchen.superlyricapi.v4.ILyricFrameListener$Stub",
                "com.hchen.superlyricapi.ISuperLyricReceiver$Stub",
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

    public static java.util.Map<String, Object> retire() {
        retired = true;
        try {
            if (!dispatchLock.writeLock().tryLock(8, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Owned Binder callback did not drain");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(error);
        }
        try {
            synchronized (CallbackEndpoints.class) {
                for (java.util.Map.Entry<IBinder, IBinder.DeathRecipient> entry : deaths) {
                    entry.getKey().unlinkToDeath(entry.getValue(), 0);
                }
                deaths.clear(); delegates.clear(); endpoints.clear(); released.clear();
                java.util.Map<String, Object> state = new java.util.HashMap<>();
                state.put("frame", frameEndpoint); state.put("legacy", legacyEndpoint);
                return state;
            }
        } finally { dispatchLock.writeLock().unlock(); }
    }
}
