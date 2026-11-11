package com.hchen.superlyric.reload

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReloadResourcesTest {
    @Test fun `retirement interrupts and joins owned workers only`() {
        val host = Executors.newSingleThreadExecutor()
        val module = ReloadResources.own(Executors.newSingleThreadExecutor())
        val started = CountDownLatch(1)
        module.execute { started.countDown(); try { CountDownLatch(1).await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() } }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            ReloadResources.retiring = true
            ReloadResources.retireExecutors()
            assertTrue(module.isTerminated)
            assertFalse(host.isShutdown)
        } finally { host.shutdownNow(); module.shutdownNow(); ReloadResources.retiring = false }
    }

    @Test fun `retired generation cannot create new executor roots`() {
        val executor = Executors.newSingleThreadExecutor()
        ReloadResources.retiring = true
        try {
            assertThrows(IllegalStateException::class.java) { ReloadResources.own(executor) }
            assertTrue(executor.isTerminated)
        } finally { executor.shutdownNow(); ReloadResources.retiring = false }
    }
}
