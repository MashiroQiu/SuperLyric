/*
 * This file is part of SuperLyric. Licensed under the GNU General Public License, version 3.
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.lyricon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.hchen.superlyric.BuildConfig
import com.hchen.superlyric.reload.ReloadResources
import com.hchen.superlyric.reload.ProviderWorkers
import com.hchen.superlyricapi.LyricListener
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.Receiver
import com.hchen.superlyricapi.SuperLyric
import com.hchen.superlyricapi.model.Stop
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import java.util.concurrent.Executors

/** All view/provider state is main-thread-owned; Binder registration runs off the UI thread. */
object LyriconBridge {
    private const val TAG = "SuperLyric-Lyricon"
    private const val CENTRAL_BOOT = "io.github.proify.lyricon.lyric.bridge.CENTRAL_BOOT_COMPLETED"
    const val SERVICE_READY = "com.hchen.superlyric.SERVICE_READY"
    private val handler = Handler(Looper.getMainLooper())
    private val worker = ReloadResources.own(Executors.newSingleThreadExecutor())
    @Volatile private var retiring = false
    @Volatile private var screenOn = false
    private var app: Context? = null
    private var broadcastRegistered = false
    private var provider: LyriconProvider? = null
    private var publisher: String? = null
    private var cachedSong: Song? = null
    private var cachedLyric: com.hchen.superlyricapi.model.Lyric? = null
    private var lastPlayback: PlaybackState? = null
    private var retryCount = 0
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null
    private val retry = Runnable { register() }
    private val listener = object : LyricListener {
        override fun onLyric(view: LyricView) { if (retiring) return; handler.post { safely { if (screenOn) receive(view) } } }
        override fun onStop(stop: Stop) { if (retiring) return; handler.post { safely {
            if (stop.publisher == null || stop.publisher == publisher) {
                provider?.player?.setPlaybackState(false)
            }
        } } }
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { safely { bindController() } }
    private val broadcasts = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { safely {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(retry)
                    provider?.player?.setPlaybackState(false)
                    unbindController()
                    manager?.removeOnActiveSessionsChangedListener(sessions)
                    worker.execute { runCatching { SuperLyric.receiver().unregister(listener) }
                        .onFailure { Log.w(TAG, "Receiver detach failed", it) } }
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    retryCount = 0
                    manager?.addOnActiveSessionsChangedListener(sessions, null, handler)
                    register()
                }
                CENTRAL_BOOT -> {
                    cachedSong?.let { song ->
                        val playerPackage = publisher ?: return@safely
                        val playback = lastPlayback
                        releaseProvider()
                        createProvider(playerPackage)
                        provider?.player?.setSong(song)
                        provider?.player?.setDisplayTranslation(true)
                        lastPlayback = playback
                        if (screenOn) playback?.let { provider?.player?.setPlaybackState(it) }
                        else provider?.player?.setPlaybackState(false)
                    }
                    if (screenOn) register()
                }
                SERVICE_READY -> if (screenOn) {
                    worker.execute { runCatching { com.hchen.superlyric.reload.SdkWorkers.resetTransport() }
                        .onFailure { Log.w(TAG, "Transport reset failed", it) } }
                    retryCount = 0; register()
                }
            }
        } }
    }

    @JvmStatic fun start(context: Context) { handler.post { safely {
        if (app != null) return@safely
        app = context.applicationContext
        manager = app?.getSystemService(MediaSessionManager::class.java)
        screenOn = app?.getSystemService(PowerManager::class.java)?.isInteractive == true
        ContextCompat.registerReceiver(app!!, broadcasts, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(CENTRAL_BOOT); addAction(SERVICE_READY)
        }, ContextCompat.RECEIVER_EXPORTED)
        broadcastRegistered = true
        if (screenOn) {
            manager?.addOnActiveSessionsChangedListener(sessions, null, handler)
            register()
        }
    } } }

    private fun register() {
        handler.removeCallbacks(retry)
        if (retiring || !screenOn) return
        worker.execute {
            val registered = runCatching { SuperLyric.receiver().register(listener) == Receiver.RegisterResult.OK }
                .getOrDefault(false)
            val latest = if (registered) runCatching { SuperLyric.receiver().latest() }.getOrNull() else null
            handler.post { safely {
                if (!screenOn) return@safely
                if (registered) {
                    retryCount = 0
                    if (latest != null) receive(latest)
                    else provider?.player?.setPlaybackState(false)
                } else if (++retryCount < 6) handler.postDelayed(retry, 5_000)
            } }
        }
    }

    private fun createProvider(packageName: String) {
        val context = app ?: return
        provider = LyriconFactory.createProvider(context, providerPackageName = BuildConfig.APPLICATION_ID,
            playerPackageName = packageName, processName = null).also {
            ProviderWorkers.track(it)
            it.autoSync = false
            it.register()
        }
        publisher = packageName
    }

    private fun receive(view: LyricView) {
        val packageName = view.publisher?.takeIf { it.isNotBlank() } ?: return
        if (publisher != packageName || provider == null) {
            releaseProvider()
            createProvider(packageName)
        }
        val player = provider?.player ?: return
        // STATE frames reuse the same Lyric instance; do not rebuild layouts on every line/state.
        if (cachedLyric !== view.lyric) {
            cachedLyric = view.lyric
            cachedSong = LyriconSongMapper.song(view.lyric)
            player.setSong(cachedSong)
            player.setDisplayTranslation(true)
        }
        val native = controller?.takeIf { it.packageName == packageName }?.playbackState
        if (native != null) syncPlayback(native)
        else {
            val state = when (view.state?.name) {
                "PLAYING" -> PlaybackState.STATE_PLAYING
                "PAUSED" -> PlaybackState.STATE_PAUSED
                "BUFFERING" -> PlaybackState.STATE_BUFFERING
                "STOPPED" -> PlaybackState.STATE_STOPPED
                "ERROR" -> PlaybackState.STATE_ERROR
                else -> null
            }
            if (state != null) syncPlayback(PlaybackState.Builder().setState(state, view.position, view.speed,
                SystemClock.elapsedRealtime()).build())
        }
        bindController()
    }

    private fun unbindController() {
        callback?.let { controller?.unregisterCallback(it) }
        callback = null
        controller = null
    }

    private fun bindController() {
        if (!screenOn) return
        val candidates = manager?.getActiveSessions(null).orEmpty().filter { it.packageName == publisher }
        val next = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull()
        if (next?.sessionToken == controller?.sessionToken) return
        unbindController()
        controller = next
        callback = next?.let { bound -> object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) { safely {
                if (controller === bound && screenOn) state?.let(::syncPlayback)
            } }
            override fun onSessionDestroyed() { safely {
                if (controller === bound) {
                    provider?.player?.setPlaybackState(false)
                    unbindController(); bindController()
                }
            } }
        } }
        callback?.let { next?.registerCallback(it, handler) }
        next?.playbackState?.let(::syncPlayback)
    }

    private fun syncPlayback(state: PlaybackState) {
        lastPlayback = state
        if (screenOn) provider?.player?.setPlaybackState(state)
    }

    private fun releaseProvider() {
        unbindController()
        provider?.let { previous ->
            runCatching { previous.player.setPlaybackState(false); previous.player.setSong(null) }
            previous.destroy()
            ProviderWorkers.release(previous)
        }
        provider = null
        publisher = null
        cachedSong = null
        cachedLyric = null
        lastPlayback = null
    }

    @JvmStatic fun close() {
        retiring = true
        ReloadResources.onMain {
            handler.removeCallbacksAndMessages(null)
            if (broadcastRegistered) app?.unregisterReceiver(broadcasts)
            broadcastRegistered = false
            manager?.removeOnActiveSessionsChangedListener(sessions)
            releaseProvider()
            ProviderWorkers.unregisterBootReceiver(app)
            app = null
            null
        }
        ReloadResources.stop(worker)
        SuperLyric.receiver().unregister(listener)
    }

    private inline fun safely(action: () -> Unit) {
        if (retiring) return
        try { action() } catch (error: Exception) { Log.e(TAG, "Bridge operation failed", error) }
    }
}
