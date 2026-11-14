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
package com.hchen.superlyric.core

import android.media.session.PlaybackState
import com.hchen.superlyric.publisher.PlaybackTracker
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Lyric
import com.hchen.superlyricapi.wire.Frame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PublisherClockOwnershipTest {
    @Test
    fun `late inactive publisher caches its lyrics without changing the active clock`() {
        mockConstruction(PlaybackTracker::class.java).use { trackers ->
            val runtime = LyricRuntime(Any())
            val registryField = LyricRuntime::class.java.getDeclaredField("publishers").apply { isAccessible = true }
            val registry = registryField.get(runtime) as PublisherRegistry
            val active = registry.getOrCreateSession("playing")
            active.playbackState = mock(PlaybackState::class.java).also {
                `when`(it.state).thenReturn(PlaybackState.STATE_PLAYING)
            }
            registry.activePublisher = "playing"
            PublisherRegistry.register("inactive", 82)
            try {
                val lyric = Lyric("inactive:old", "Old song", "Artist", "", 2000,
                    listOf(Line("Old lyric", 0, 2000)))
                val result = runtime.publishFrame(82, "inactive", 1000, Frame.full(lyric, null, "inactive").encode())
                assertEquals(LyricRuntime.RESULT_OK, result)
                assertEquals("playing", registry.activePublisher)
                assertEquals("inactive:old", registry.sessions["inactive"]!!.lyric!!.id)
                verify(trackers.constructed().single(), never()).setLyrics(any())
                verify(trackers.constructed().single(), never()).onPlaybackStateChanged(any())
                verify(trackers.constructed().single(), never()).onSessionPositionSynced(anyLong())
            } finally {
                PublisherRegistry.removeProcess("inactive", 82)
            }
        }
    }
}
