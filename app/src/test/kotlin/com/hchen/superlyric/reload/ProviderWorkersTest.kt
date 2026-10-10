package com.hchen.superlyric.reload

import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.service.RemoteService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProviderWorkersTest {
    @Test fun pinnedSdkScopesAreJoinedBeforeRetirementReturns() {
        val providerType = Class.forName("io.github.proify.lyricon.provider.impl.ProviderV27Impl")
            .asSubclass(LyriconProvider::class.java)
        val remoteType = Class.forName("io.github.proify.lyricon.provider.impl.RemoteServiceProxy")
            .asSubclass(RemoteService::class.java)
        val provider = mock(providerType)
        val remote = mock(remoteType)
        val registration = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val callbacks = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        providerType.getDeclaredField("coroutineScope").apply {
            isAccessible = true; set(provider, registration)
        }
        remoteType.getDeclaredField("callbackScope").apply {
            isAccessible = true; set(remote, callbacks)
        }
        `when`(provider.service).thenReturn(remote)
        ProviderWorkers.track(provider)
        val started = CountDownLatch(1)
        val cleanup = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        callbacks.launch {
            try { started.countDown(); awaitCancellation() }
            finally { withContext(NonCancellable) {
                cleanup.countDown(); check(release.await(5, TimeUnit.SECONDS))
            } }
        }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val result = worker.submit { ProviderWorkers.retire() }
            assertTrue(cleanup.await(2, TimeUnit.SECONDS))
            assertFalse(result.isDone)
            release.countDown()
            result.get(2, TimeUnit.SECONDS)
            assertTrue(registration.coroutineContext[Job]!!.isCompleted)
            assertTrue(callbacks.coroutineContext[Job]!!.isCompleted)
        } finally {
            release.countDown(); registration.cancel(); callbacks.cancel()
            worker.shutdownNow(); ProviderWorkers.retire()
        }
    }
}
