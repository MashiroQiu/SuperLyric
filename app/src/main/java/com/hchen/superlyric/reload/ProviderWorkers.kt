package com.hchen.superlyric.reload

import android.content.BroadcastReceiver
import android.content.Context
import io.github.proify.lyricon.provider.LyriconProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArraySet

/** The pinned provider SDK 0.1.70 has no global shutdown API. Release its owned roots explicitly. */
object ProviderWorkers {
    private val jobs = CopyOnWriteArraySet<Job>()

    private fun scope(target: Any, name: String): CoroutineScope {
        val field = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        return field.get(target) as CoroutineScope
    }

    @JvmStatic fun track(provider: LyriconProvider) {
        jobs.add(checkNotNull(scope(provider, "coroutineScope").coroutineContext[Job]))
        jobs.add(checkNotNull(scope(provider.service, "callbackScope").coroutineContext[Job]))
    }

    @JvmStatic fun unregisterBootReceiver(context: Context?) {
        if (context == null) return
        val type = Class.forName("io.github.proify.lyricon.provider.CentralServiceReceiver")
        val initialized = type.getDeclaredField("isInitialized").apply { isAccessible = true }
        if (!initialized.getBoolean(null)) return
        val receiver = type.getDeclaredField("innerReceiver").apply { isAccessible = true }
            .get(null) as BroadcastReceiver
        context.applicationContext.unregisterReceiver(receiver)
        initialized.setBoolean(null, false)
    }

    @JvmStatic fun retire() {
        val snapshot = jobs.toList()
        snapshot.forEach { it.cancel() }
        runBlocking { withTimeout(8000) { snapshot.forEach { it.join() } } }
        jobs.clear()
    }
}
