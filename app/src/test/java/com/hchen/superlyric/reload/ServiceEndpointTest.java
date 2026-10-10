package com.hchen.superlyric.reload;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import io.github.libxposed.api.XposedInterface;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ServiceEndpointTest {
    private Map<String, Object> state(Binder binder) {
        Map<String, Object> state = new HashMap<>();
        state.put("binder", binder);
        state.put("failed", new AtomicBoolean());
        state.put("lock", new ReentrantReadWriteLock(true));
        state.put("delegate", new AtomicReference<IBinder>());
        state.put("ready", new AtomicReference<>(new CountDownLatch(1)));
        return state;
    }
    private XposedInterface.Chain chain(Binder binder, Parcel data, Parcel reply) {
        XposedInterface.Chain chain = mock(XposedInterface.Chain.class);
        when(chain.getThisObject()).thenReturn(binder);
        when(chain.getArg(0)).thenReturn(7);
        when(chain.getArg(1)).thenReturn(data);
        when(chain.getArg(2)).thenReturn(reply);
        when(chain.getArg(3)).thenReturn(0);
        return chain;
    }
    private Object invoke(ServiceEndpoint endpoint, XposedInterface.Chain chain) {
        try { return endpoint.hooker().intercept(chain); }
        catch (Throwable error) { throw new CompletionException(error); }
    }
    @Test public void oldClientAndInFlightHookReachNewGeneration() throws Exception {
        Binder binder = mock(Binder.class);
        Parcel data = mock(Parcel.class), reply = mock(Parcel.class);
        ServiceEndpoint first = new ServiceEndpoint(state(binder));
        IBinder old = mock(IBinder.class), next = mock(IBinder.class);
        when(old.transact(7, data, reply, 0)).thenReturn(true);
        when(next.transact(7, data, reply, 0)).thenReturn(false);
        XposedInterface.Chain request = chain(binder, data, reply);
        first.publish(old);
        assertEquals(true, invoke(first, request));
        first.beginRetirement();
        Map<String, Object> saved = first.save();
        assertNull(((AtomicReference<?>) saved.get("delegate")).get());
        ServiceEndpoint second = new ServiceEndpoint(saved);
        second.publish(next);
        assertSame(first.binder, second.binder);
        assertEquals(false, invoke(first, request));
        assertEquals(false, invoke(second, request));
        verify(old, times(1)).transact(7, data, reply, 0);
        verify(next, times(2)).transact(7, data, reply, 0);
    }
    @Test public void transactionWaitsDuringHandoverWithoutRestartingClient() throws Exception {
        Binder binder = mock(Binder.class);
        Parcel data = mock(Parcel.class), reply = mock(Parcel.class);
        ServiceEndpoint first = new ServiceEndpoint(state(binder));
        first.publish(mock(IBinder.class));
        first.beginRetirement();
        ServiceEndpoint next = new ServiceEndpoint(first.save());
        IBinder delegate = mock(IBinder.class);
        when(delegate.transact(7, data, reply, 0)).thenReturn(true);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch started = new CountDownLatch(1);
            Future<Object> result = worker.submit(() -> { started.countDown(); return invoke(first, chain(binder, data, reply)); });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            next.publish(delegate);
            assertEquals(true, result.get(2, TimeUnit.SECONDS));
        } finally { worker.shutdownNow(); }
    }
    @Test public void liveDelegateCannotBeExportedAsReloadState() {
        ServiceEndpoint endpoint = new ServiceEndpoint(state(mock(Binder.class)));
        endpoint.publish(mock(IBinder.class));
        assertThrows(IllegalStateException.class, endpoint::save);
        endpoint.beginRetirement();
        assertNull(((AtomicReference<?>) endpoint.save().get("delegate")).get());
    }
    @Test public void failedHandoverFailsPromptlyInsteadOfSpinning() throws Exception {
        ServiceEndpoint endpoint = new ServiceEndpoint(state(mock(Binder.class)));
        endpoint.publish(mock(IBinder.class));
        endpoint.beginRetirement();
        endpoint.fail();
        CompletionException error = assertThrows(CompletionException.class,
            () -> invoke(endpoint, chain(endpoint.binder, mock(Parcel.class), mock(Parcel.class))));
        assertTrue(error.getCause() instanceof android.os.RemoteException);
    }
    @Test public void unrelatedBinderTransactionsProceedNormally() throws Throwable {
        ServiceEndpoint endpoint = new ServiceEndpoint(state(mock(Binder.class)));
        XposedInterface.Chain request = mock(XposedInterface.Chain.class);
        when(request.getThisObject()).thenReturn(mock(Binder.class));
        when(request.proceed()).thenReturn("host transaction");
        assertEquals("host transaction", endpoint.hooker().intercept(request));
    }
}
