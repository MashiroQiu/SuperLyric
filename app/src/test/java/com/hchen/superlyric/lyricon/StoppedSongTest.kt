/*
 * This file is part of SuperLyric.
 *
 * SuperLyric is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2025-2026 HChenX
 */
package com.hchen.superlyric.lyricon

import android.media.session.PlaybackState
import android.os.Handler
import com.hchen.superlyricapi.LyricListener
import com.hchen.superlyricapi.model.Lyric
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Stop
import com.hchen.superlyricapi.model.StopReason
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.RemotePlayer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class StoppedSongTest {
    private fun field(name: String) = LyriconBridge::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test fun `cleared lyrics cannot be revived by native playback callbacks`() {
        mockConstruction(Handler::class.java) { handler, _ ->
            doAnswer { it.getArgument<Runnable>(0).run(); true }.`when`(handler).post(any(Runnable::class.java))
        }.use {
            val bridge = LyriconBridge
            val player = mock(RemotePlayer::class.java)
            val provider = mock(LyriconProvider::class.java)
            `when`(provider.player).thenReturn(player)
            field("provider").set(null, provider)
            field("publisher").set(null, "com.kugou.android.lite")
            field("cachedSong").set(null, Song(id = "old"))
            field("cachedLyric").set(null, Lyric("old", "Old", "", "", 1000, listOf(Line("old", 0, 1000))))
            val listener = field("listener").get(null) as LyricListener
            try {
                listener.onStop(Stop("old", StopReason.STOPPED, "different.player"))
                assertNotNull(field("cachedSong").get(null))
                verifyNoInteractions(player)
                listener.onStop(Stop("old", StopReason.STOPPED, "com.kugou.android.lite"))
                assertNull(field("cachedSong").get(null))
                assertNull(field("cachedLyric").get(null))
                assertNull(field("lastPlayback").get(null))
                verify(player).setSong(null)
                verify(player).setPlaybackState(false)
                clearInvocations(player)
                val state = mock(PlaybackState::class.java)
                LyriconBridge::class.java.getDeclaredMethod("syncPlayback", PlaybackState::class.java)
                    .apply { isAccessible = true }.invoke(bridge, state)
                verifyNoInteractions(player)
            } finally {
                field("provider").set(null, null)
                field("publisher").set(null, null)
                field("cachedSong").set(null, null)
                field("cachedLyric").set(null, null)
            }
        }
    }
}
