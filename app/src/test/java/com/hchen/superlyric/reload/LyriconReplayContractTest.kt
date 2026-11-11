package com.hchen.superlyric.reload

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.CachedRemotePlayer
import io.github.proify.lyricon.provider.RemotePlayer
import java.lang.reflect.Proxy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LyriconReplayContractTest {
    private class Connection {
        var connected = false
        val delivered = mutableListOf<Pair<String, Any?>>()
        val channel = Proxy.newProxyInstance(RemotePlayer::class.java.classLoader,
            arrayOf(RemotePlayer::class.java)) { _, method, arguments ->
            if (connected) delivered.add(method.name to arguments?.firstOrNull())
            connected
        } as RemotePlayer
        val player = CachedRemotePlayer(channel)
        fun connect() {
            connected = true
            player.javaClass.getDeclaredMethod("syncs\$provider").invoke(player)
        }
    }

    @Test fun `song published before asynchronous registration is replayed at connection`() {
        val connection = Connection()
        val song = Song(id = "first")
        assertFalse(connection.player.setSong(song))
        connection.player.setDisplayTranslation(true)
        connection.player.setPlaybackState(true)
        assertTrue(connection.delivered.isEmpty())
        connection.connect()
        assertTrue(connection.delivered.any { it.first == "setSong" && it.second === song })
        assertTrue(connection.delivered.any { it.first == "setPlaybackState" && it.second == true })
        assertTrue(connection.delivered.any { it.first == "setDisplayTranslation" && it.second == true })
    }

    @Test fun `second player receives its own song after the first player is paused`() {
        val first = Connection()
        val second = Connection()
        first.player.setSong(Song(id = "first"))
        first.player.setPlaybackState(true)
        first.connect()
        first.player.setPlaybackState(false)
        val secondSong = Song(id = "second")
        second.player.setSong(secondSong)
        second.player.setPlaybackState(true)
        second.connect()
        assertEquals(listOf(secondSong), second.delivered.filter { it.first == "setSong" }.map { it.second })
        assertTrue(second.delivered.any { it.first == "setPlaybackState" && it.second == true })
    }

    @Test fun `reconnection replays the newest pending song instead of the first song`() {
        val connection = Connection()
        connection.player.setSong(Song(id = "old"))
        connection.connect()
        connection.connected = false
        val newest = Song(id = "new")
        connection.player.setSong(newest)
        connection.delivered.clear()
        connection.connect()
        assertEquals(listOf(newest), connection.delivered.filter { it.first == "setSong" }.map { it.second })
    }
}
