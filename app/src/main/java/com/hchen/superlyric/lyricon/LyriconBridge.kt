/*
 * This file is part of SuperLyric.
 * Licensed under the GNU General Public License, version 3.
 * Copyright (C) 2026 HChenX
 */
package com.hchen.superlyric.lyricon

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.hchen.superlyric.BuildConfig
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider

/** One bridge per System UI process; all mutable state belongs to the main handler. */
object LyriconBridge {
    private const val TAG = "SuperLyric-Lyricon"
    private val handler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var provider: LyriconProvider? = null
    private var publisher: String? = null
    private var trackKey: String? = null
    private var fullSong = false
    private var cachedSong: Song? = null
    private var playbackCallback: MediaController.Callback? = null
    private var controller: MediaController? = null
    private var sessionManager: MediaSessionManager? = null

    private val receiver = object : ISuperLyricReceiver.Stub() {
        override fun onLyric(publisher: String?, data: SuperLyricData?) {
            if (publisher.isNullOrBlank() || data == null) return
            handler.post { safely { receive(publisher, data) } }
        }

        override fun onStop(publisher: String?, data: SuperLyricData?) {
            handler.post {
                safely {
                    // A delayed stop from the previous player must not hide the new player.
                    if (publisher == this@LyriconBridge.publisher) {
                        provider?.player?.setPlaybackState(false)
                    }
                }
            }
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        safely { bindController() }
    }

    @JvmStatic
    fun start(appContext: Context) {
        handler.post {
            if (context != null) return@post
            context = appContext.applicationContext
            safely {
                sessionManager = context?.getSystemService(MediaSessionManager::class.java)
                // System UI already holds MEDIA_CONTENT_CONTROL; no notification access prompt.
                sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, null, handler)
            }
            registerReceiver()
        }
    }

    private fun registerReceiver() {
        safely {
            if (SuperLyricHelper.isAvailable()) {
                SuperLyricHelper.registerReceiver(receiver)
                if (SuperLyricHelper.isReceiverRegistered(receiver)) return
            }
        }
        // AMS can finish publishing the binder after System UI Application.onCreate.
        handler.postDelayed(::registerReceiver, 5_000)
    }

    private fun receive(packageName: String, incoming: SuperLyricData) {
        val newPublisher = publisher != packageName
        val key = LyriconSongMapper.trackKey(incoming)
        val newTrack = newPublisher || (incoming.hasLyricId() && key != trackKey)
        if (newPublisher) {
            releaseProvider()
            val appContext = context ?: return
            val next = LyriconFactory.createProvider(
                context = appContext,
                providerPackageName = BuildConfig.APPLICATION_ID,
                playerPackageName = packageName,
                processName = null
            )
            provider = next
            publisher = packageName
            next.autoSync = true
            next.register()
            bindController()
        }
        val player = provider?.player ?: return
        if (newTrack) {
            fullSong = false
            cachedSong = null
            trackKey = key
            player.setSong(null)
        }

        // After System UI restarts or the active app changes, the first event may be a delta.
        val data = if (newTrack && !incoming.hasAllLyrics()) {
            SuperLyricHelper.getLatestLyric()?.takeIf {
                it.hasAllLyrics() && LyriconSongMapper.trackKey(it) == key
            } ?: incoming
        } else incoming

        if (data.hasAllLyrics()) {
            val song = LyriconSongMapper.song(data)
            fullSong = !song.lyrics.isNullOrEmpty()
            if (fullSong) {
                cachedSong = song
                player.setSong(song)
                player.setDisplayTranslation(true)
            }
        }
        if (!fullSong) {
            // Untimed legacy lyrics use text mode; timed single lines retain word/translation data.
            val line = data.currentLyric?.let(LyriconSongMapper::line)
            if (line != null) {
                val richLine = line.copy(
                    translation = line.translation ?: data.translation?.text,
                    secondary = line.secondary ?: data.secondary?.text
                )
                player.setSong(LyriconSongMapper.song(data).copy(lyrics = listOf(richLine)))
                player.setDisplayTranslation(true)
            } else {
                player.sendText(LyriconSongMapper.text(data))
            }
        }

        val state = controller?.playbackState
        if (state != null) {
            syncPlayback(state)
        } else {
            // API publishers may not expose a MediaSession. Anchor their reported position.
            val position = when {
                incoming.hasPosition() -> incoming.position
                data.hasPosition() -> data.position
                else -> data.currentLyric?.startTime ?: 0
            }.coerceAtLeast(0)
            player.setPlaybackState(
                PlaybackState.Builder().setState(
                    PlaybackState.STATE_PLAYING, position, 1f, SystemClock.elapsedRealtime()
                ).build()
            )
        }
    }

    private fun bindController() {
        val candidates = try {
            sessionManager?.getActiveSessions(null).orEmpty().filter { it.packageName == publisher }
        } catch (error: SecurityException) {
            Log.w(TAG, "Media session access unavailable; using SuperLyric progress", error)
            emptyList()
        }
        val next = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull()
        if (controller?.sessionToken == next?.sessionToken) return
        playbackCallback?.let { controller?.unregisterCallback(it) }
        controller = next
        playbackCallback = next?.let { bound ->
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (controller !== bound) return
                    safely { syncPlayback(state) }
                }
                override fun onSessionDestroyed() {
                    if (controller !== bound) return
                    safely {
                        provider?.player?.setPlaybackState(false)
                        controller = null
                        playbackCallback = null
                        bindController()
                    }
                }
            }
        }
        playbackCallback?.let { next?.registerCallback(it, handler) }
        syncPlayback(next?.playbackState)
    }

    private fun syncPlayback(state: PlaybackState?) {
        if (state == null) return
        val player = provider?.player ?: return
        if (state.state == PlaybackState.STATE_PLAYING && !fullSong) {
            cachedSong?.let {
                player.setSong(it)
                fullSong = true
            }
        }
        player.setPlaybackState(state)
        if (state.state == PlaybackState.STATE_STOPPED || state.state == PlaybackState.STATE_NONE
            || state.state == PlaybackState.STATE_ERROR) {
            player.setSong(null)
            fullSong = false
        }
    }

    private fun releaseProvider() {
        playbackCallback?.let { controller?.unregisterCallback(it) }
        playbackCallback = null
        controller = null
        cachedSong = null
        trackKey = null
        fullSong = false
        val previous = provider
        provider = null
        publisher = null
        previous?.player?.setPlaybackState(false)
        previous?.player?.setSong(null)
        previous?.destroy()
    }

    private inline fun safely(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            // Optional output failures must not crash System UI or disrupt the original API.
            Log.e(TAG, "Lyricon bridge operation failed", error)
        }
    }
}
