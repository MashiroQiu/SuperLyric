package com.hchen.superlyricapi

import com.hchen.superlyricapi.internal.Background
import com.hchen.superlyricapi.internal.BinderHub
import com.hchen.superlyricapi.internal.FrameSink
import com.hchen.superlyricapi.internal.LyricCache
import com.hchen.superlyricapi.internal.ReceiverImpl
import com.hchen.superlyricapi.internal.Transport
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Lyric
import com.hchen.superlyricapi.model.PlaybackState
import com.hchen.superlyricapi.model.State
import com.hchen.superlyricapi.model.Stop
import com.hchen.superlyricapi.model.StopReason
import com.hchen.superlyricapi.wire.Frame
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class StaleCacheRegressionTest {
    private class Fake : Transport {
        @Volatile var frame: Frame? = null
        var sink: FrameSink? = null
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        override fun latest(): Frame? {
            val captured = frame
            if (calls.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            return captured
        }
        override fun publish(lyric: Lyric) = 0
        override fun clear() = Unit
        override fun register(sink: FrameSink): Int { this.sink = sink; return 0 }
        override fun unregister() { sink = null }
        override fun apiVersion() = 40
    }

    private fun full(id: String, publisher: String = "music") = Frame.full(
        Lyric(id, id, "artist", "", 5_000, listOf(Line(id, 0, 5_000))),
        State(id, 0, 100, 1f, PlaybackState.PLAYING), publisher)
    private fun state(id: String, publisher: String = "music", position: Long = 100) =
        Frame.state(State(id, 0, position, 1f, PlaybackState.PLAYING), publisher)
    private fun idle() {
        val done = CountDownLatch(1)
        Background.execute { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }

    @Test fun lateHealCannotReplaceANewerFullSong() {
        val fake = Fake().apply { frame = full("old") }
        val seen = CopyOnWriteArrayList<String>()
        val cache = LyricCache(fake) { seen.add(it.lyric.id) }
        try {
            cache.resolve(state("old"))
            assertTrue(fake.entered.await(5, TimeUnit.SECONDS))
            cache.resolve(full("new"))
        } finally { fake.release.countDown() }
        idle()
        assertEquals("new", cache.current()?.lyric?.id)
        assertTrue(seen.isEmpty())
    }

    @Test fun clearingTheCacheInvalidatesAnInflightHeal() {
        val fake = Fake().apply { frame = full("old") }
        val seen = CopyOnWriteArrayList<LyricView>()
        val cache = LyricCache(fake) { seen.add(it) }
        try {
            cache.resolve(state("old"))
            assertTrue(fake.entered.await(5, TimeUnit.SECONDS))
            cache.clear()
        } finally { fake.release.countDown() }
        idle()
        assertNull(cache.current())
        assertTrue(seen.isEmpty())
    }

    @Test fun aNewMissingTrackSupersedesTheInflightRequest() {
        val fake = Fake().apply { frame = full("old") }
        val seen = CopyOnWriteArrayList<String>()
        val cache = LyricCache(fake) { seen.add(it.lyric.id) }
        try {
            cache.resolve(state("old"))
            assertTrue(fake.entered.await(5, TimeUnit.SECONDS))
            fake.frame = full("new")
            cache.resolve(state("new"))
        } finally { fake.release.countDown() }
        idle()
        assertEquals(listOf("new"), seen.toList())
        assertEquals("new", cache.current()?.lyric?.id)
    }

    @Test fun repeatedStatesShareOneHealAndKeepTheNewestPosition() {
        val fake = Fake().apply { frame = full("same") }
        val cache = LyricCache(fake) { }
        try {
            cache.resolve(state("same"))
            assertTrue(fake.entered.await(5, TimeUnit.SECONDS))
            repeat(100) { cache.resolve(state("same", position = 1_000L + it)) }
        } finally { fake.release.countDown() }
        idle()
        assertEquals(1, fake.calls.get())
        assertEquals(1_099L, cache.current()?.position)
    }

    @Test fun equalTrackIdsFromDifferentPublishersDoNotShareLyrics() {
        val fake = Fake().apply { frame = full("same", "second"); release.countDown() }
        val seen = CountDownLatch(1)
        val cache = LyricCache(fake) { seen.countDown() }
        cache.resolve(full("same", "first"))
        assertNull(cache.resolve(state("same", "second")))
        assertTrue(seen.await(5, TimeUnit.SECONDS))
        assertEquals("second", cache.current()?.publisher)
    }

    @Test fun aBlockedLatestCallCannotRollbackAReceivedNewSong() {
        val fake = Fake().apply { frame = full("old") }
        val result = AtomicReference<LyricView?>()
        val listener = object : LyricListener {
            override fun onLyric(view: LyricView) = Unit
            override fun onStop(stop: Stop) = Unit
        }
        ReceiverImpl.unregisterAll()
        BinderHub.injectForTest(fake)
        val fetch = Thread { result.set(ReceiverImpl.latest()) }
        try {
            ReceiverImpl.register(listener)
            fetch.start()
            assertTrue(fake.entered.await(5, TimeUnit.SECONDS))
            fake.sink!!.onFrame(full("new"))
            fake.frame = full("new")
            fake.release.countDown()
            fetch.join(5_000)
            assertFalse(fetch.isAlive)
            assertEquals("new", result.get()?.lyric?.id)
        } finally {
            fake.release.countDown(); fetch.join(5_000)
            ReceiverImpl.unregisterAll(); BinderHub.injectForTest(null)
        }
    }

    @Test fun aStopFromThePreviousSongCannotClearTheNewSong() {
        val fake = Fake().apply { release.countDown() }
        var stopped = false
        ReceiverImpl.unregisterAll(); BinderHub.injectForTest(fake)
        try {
            ReceiverImpl.register(object : LyricListener {
                override fun onLyric(view: LyricView) = Unit
                override fun onStop(stop: Stop) { stopped = true }
            })
            fake.sink!!.onFrame(full("new"))
            fake.sink!!.onFrame(Frame.stop(Stop("old", StopReason.CLEARED, "music")))
            assertFalse(stopped)
        } finally { ReceiverImpl.unregisterAll(); BinderHub.injectForTest(null) }
    }
}
