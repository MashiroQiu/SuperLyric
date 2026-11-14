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

import android.os.Binder
import android.os.Bundle
import android.os.IInterface
import android.os.IBinder
import android.os.RemoteCallbackList
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.v4.ILyricFrameListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@Suppress("DEPRECATION")
class BroadcasterReloadTest {
    @Test
    fun `frame handover preserves subscribers and uid quota`() = withBundles {
        val old = FrameBroadcaster()
        val listeners = List(16) { FrameListener() }
        listeners.forEach { assertEquals(FrameBroadcaster.REGISTER_OK, old.register(it, 7, 1, "player")) }
        val saved = old.saveAndClose()
        assertFalse(old.hasReceivers())
        val fresh = FrameBroadcaster()
        fresh.restore(saved)
        assertTrue(fresh.hasReceivers())
        assertEquals(FrameBroadcaster.REGISTER_QUOTA, fresh.register(FrameListener(), 7, 1, "player"))
        fresh.broadcast(byteArrayOf(1))
        assertTrue(listeners.all { it.frames == 1 })
        var idle = false
        fresh.setOnInterestChanged { idle = !fresh.hasReceivers() }
        listeners.forEach { fresh.unregister(it, 1, "player") }
        assertTrue(idle)
        assertEquals(FrameBroadcaster.REGISTER_OK, fresh.register(FrameListener(), 7, 1, "player"))
        fresh.saveAndClose()
    }

    @Test
    fun `legacy handover preserves quota and full lyric capability`() = withBundles {
        val old = ReceiverBroadcaster(Callbacks())
        val receivers = List(16) { LegacyReceiver() }
        receivers.forEach { old.register(it, 9, 1, "player", true) }
        val saved = old.saveAndClose()
        assertFalse(old.hasReceivers())
        val fresh = ReceiverBroadcaster(Callbacks())
        fresh.restore(saved)
        val overflow = LegacyReceiver()
        fresh.register(overflow, 9, 1, "player", false)
        assertFalse(fresh.isRegistered(overflow))
        var fullCount = 0
        fresh.broadcast("test") { _, full -> if (full) fullCount++ }
        assertEquals(16, fullCount)
        var idle = false
        fresh.setOnInterestChanged { idle = !fresh.hasReceivers() }
        receivers.forEach { fresh.unregister(it, 1, "player") }
        assertTrue(idle)
        fresh.register(overflow, 9, 1, "player", false)
        assertTrue(fresh.isRegistered(overflow))
        fresh.saveAndClose()
    }

    @Test
    fun `dead frame listener disables dispatch interest`() {
        val broadcaster = FrameBroadcaster()
        val listener = FrameListener()
        assertEquals(FrameBroadcaster.REGISTER_OK, broadcaster.register(listener, 7, 1, "player"))
        var idle = false
        broadcaster.setOnInterestChanged { idle = !broadcaster.hasReceivers() }
        listener.binder.death!!.binderDied()
        assertTrue(idle)
    }

    private fun withBundles(action: () -> Unit) {
        Mockito.mockConstruction(Bundle::class.java) { bundle, _ ->
            val values = mutableMapOf<String, Any?>()
            Mockito.doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Any?>(1); null }
                .`when`(bundle).putInt(Mockito.anyString(), Mockito.anyInt())
            Mockito.doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Any?>(1); null }
                .`when`(bundle).putBoolean(Mockito.anyString(), Mockito.anyBoolean())
            Mockito.doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Any?>(1); null }
                .`when`(bundle).putBinder(Mockito.anyString(), Mockito.any(IBinder::class.java))
            Mockito.doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Any?>(1); null }
                .`when`(bundle).putBundle(Mockito.anyString(), Mockito.any(Bundle::class.java))
            Mockito.`when`(bundle.getInt(Mockito.anyString())).thenAnswer { values[it.getArgument<String>(0)] ?: 0 }
            Mockito.`when`(bundle.getBoolean(Mockito.anyString())).thenAnswer { values[it.getArgument<String>(0)] ?: false }
            Mockito.`when`(bundle.getBinder(Mockito.anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            Mockito.`when`(bundle.getBundle(Mockito.anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            Mockito.`when`(bundle.keySet()).thenAnswer { values.keys }
        }.use { action() }
    }

    private class AliveBinder(private val owner: IInterface) : Binder() {
        override fun queryLocalInterface(descriptor: String): IInterface = owner
        var death: IBinder.DeathRecipient? = null
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) { death = recipient }
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean { death = null; return true }
        override fun isBinderAlive() = true
    }
    private class FrameListener : ILyricFrameListener {
        val binder = AliveBinder(this)
        var frames = 0
        override fun asBinder(): IBinder = binder
        override fun onFrame(frame: ByteArray) { frames++ }
    }
    private class LegacyReceiver : ISuperLyricReceiver {
        private val binder = AliveBinder(this)
        override fun asBinder(): IBinder = binder
        override fun onLyric(publisher: String, data: SuperLyricData) = Unit
        override fun onStop(publisher: String, data: SuperLyricData) = Unit
    }
    private class Callbacks : RemoteCallbackList<ISuperLyricReceiver>() {
        private val items = linkedMapOf<IBinder, ISuperLyricReceiver>()
        private var snapshot = emptyList<ISuperLyricReceiver>()
        override fun register(callback: ISuperLyricReceiver): Boolean { items[callback.asBinder()] = callback; return true }
        override fun unregister(callback: ISuperLyricReceiver) = items.remove(callback.asBinder()) != null
        override fun kill() { items.clear() }
        override fun beginBroadcast(): Int { snapshot = items.values.toList(); return snapshot.size }
        override fun getBroadcastItem(index: Int) = snapshot[index]
        override fun finishBroadcast() { snapshot = emptyList() }
    }
}
