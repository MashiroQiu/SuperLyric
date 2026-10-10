package com.hchen.superlyric.reload;

import okio.AsyncTimeout;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class OwnedOkioTest {
    private boolean watchdogAlive() {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> thread.isAlive()
            && thread.getClass().getName().equals("okio.AsyncTimeout$Watchdog")
            && thread.getClass().getClassLoader() == AsyncTimeout.class.getClassLoader());
    }
    @Test public void idleWatchdogStopsAndCanBeCreatedAgainForAnotherGeneration() {
        for (int i = 0; i < 3; i++) {
            AsyncTimeout timeout = new AsyncTimeout();
            timeout.timeout(30, TimeUnit.SECONDS);
            timeout.enter();
            assertTrue(watchdogAlive());
            timeout.exit();
            OwnedOkio.retire(AsyncTimeout.class.getClassLoader());
            assertFalse(watchdogAlive());
        }
    }
    @Test public void sharedHostLibraryIsNeverStopped() {
        AsyncTimeout timeout = new AsyncTimeout();
        timeout.timeout(30, TimeUnit.SECONDS);
        timeout.enter();
        timeout.exit();
        try {
            OwnedOkio.retire(new ClassLoader() {});
            assertTrue(watchdogAlive());
        } finally { OwnedOkio.retire(AsyncTimeout.class.getClassLoader()); }
    }
    @Test public void activeTimeoutPreventsFalseSuccessfulRetirement() {
        AsyncTimeout timeout = new AsyncTimeout();
        timeout.timeout(30, TimeUnit.SECONDS);
        timeout.enter();
        try {
            assertThrows(IllegalStateException.class, () -> OwnedOkio.retire(AsyncTimeout.class.getClassLoader()));
            assertTrue(watchdogAlive());
        } finally {
            timeout.exit();
            OwnedOkio.retire(AsyncTimeout.class.getClassLoader());
        }
    }
}
