package com.hchen.superlyric.reload;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import io.github.libxposed.api.XposedInterface;

/**
 * Clients retain a framework Binder, never a module Stub. The shared handover state contains only
 * boot-classloader objects; the delegate is cleared before the old generation is retired.
 */
public final class ServiceEndpoint {
    public static final String HOOK_ID = "superlyric.service.endpoint";
    public final Binder binder;
    private final ReentrantReadWriteLock lock;
    private final AtomicReference<IBinder> delegate;
    private final AtomicReference<CountDownLatch> ready;
    private final AtomicBoolean failed;

    public ServiceEndpoint() {
        binder = new Binder();
        binder.attachInterface(null, "com.hchen.superlyricapi.ISuperLyricManager");
        lock = new ReentrantReadWriteLock(true);
        delegate = new AtomicReference<>();
        ready = new AtomicReference<>(new CountDownLatch(1));
        failed = new AtomicBoolean();
    }

    @SuppressWarnings("unchecked")
    public ServiceEndpoint(Map<String, Object> state) {
        binder = (Binder) state.get("binder");
        lock = (ReentrantReadWriteLock) state.get("lock");
        delegate = (AtomicReference<IBinder>) state.get("delegate");
        ready = (AtomicReference<CountDownLatch>) state.get("ready");
        failed = (AtomicBoolean) state.get("failed");
    }

    public XposedInterface.Hooker hooker() {
        return chain -> {
            if (chain.getThisObject() != binder) return chain.proceed();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (true) {
                if (failed.get()) throw new RemoteException("SuperLyric reload failed; restart required");
                CountDownLatch latch = ready.get();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || !latch.await(remaining, TimeUnit.NANOSECONDS)) {
                    throw new RemoteException("SuperLyric reload timed out");
                }
                lock.readLock().lock();
                try {
                    IBinder current = delegate.get();
                    if (failed.get()) throw new RemoteException("SuperLyric reload failed; restart required");
                    if (latch != ready.get() || current == null) continue;
                    return current.transact((int) chain.getArg(0), (Parcel) chain.getArg(1),
                        (Parcel) chain.getArg(2), (int) chain.getArg(3));
                } finally {
                    lock.readLock().unlock();
                }
            }
        };
    }

    public void publish(IBinder service) {
        lock.writeLock().lock();
        try {
            failed.set(false);
            delegate.set(service);
            ready.get().countDown();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Drain transactions before closing the delegate. Call fail on any failed retirement. */
    public void beginRetirement() {
        lock.writeLock().lock();
        try {
            ready.set(new CountDownLatch(1));
            delegate.set(null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void fail() {
        failed.set(true);
        ready.get().countDown();
    }

    public Map<String, Object> save() {
        if (delegate.get() != null) throw new IllegalStateException("Old service is still reachable");
        Map<String, Object> state = new HashMap<>();
        state.put("binder", binder);
        state.put("lock", lock);
        state.put("delegate", delegate);
        state.put("ready", ready);
        state.put("failed", failed);
        return state;
    }
}
