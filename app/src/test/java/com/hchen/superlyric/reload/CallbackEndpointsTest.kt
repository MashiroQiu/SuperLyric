package com.hchen.superlyric.reload

import android.os.Binder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CallbackEndpointsTest {
    private class Stub(private val descriptor: String) : Binder() {
        override fun getInterfaceDescriptor(): String = descriptor
    }
    private fun field(name: String) = CallbackEndpoints::class.java.getDeclaredField(name).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    private fun delegates() = field("delegates").get(null) as Map<Binder, Binder>

    @BeforeEach fun freshGeneration() {
        for (name in listOf("endpoints", "delegates", "released")) (field(name).get(null) as MutableMap<*, *>).clear()
        (field("deaths").get(null) as MutableSet<*>).clear()
        field("frameEndpoint").set(null, null)
        field("legacyEndpoint").set(null, null)
        field("retired").setBoolean(null, false)
    }

    @Test fun `screen registration cycles reuse one native frame endpoint`() {
        var endpoint: android.os.IBinder? = null
        repeat(30) {
            val stub = Stub("com.hchen.superlyricapi.v4.ILyricFrameListener")
            val next = CallbackEndpoints.wrap(stub)
            if (endpoint == null) endpoint = next else assertSame(endpoint, next)
            assertSame(stub, delegates()[next])
            assertEquals(1, delegates().size)
        }
    }

    @Test fun `an old stub cannot replace the new callback delegate`() {
        val first = Stub("com.hchen.superlyricapi.v4.ILyricFrameListener")
        val second = Stub("com.hchen.superlyricapi.v4.ILyricFrameListener")
        val endpoint = CallbackEndpoints.wrap(first)
        CallbackEndpoints.wrap(second)
        CallbackEndpoints.wrap(first)
        assertSame(second, delegates()[endpoint])
    }

    @Test fun `destroyed provider exports cannot become live again`() {
        val stub = Stub("io.github.proify.lyricon.provider.IProviderBinder")
        val endpoint = CallbackEndpoints.wrap(stub)
        CallbackEndpoints.forget(stub)
        assertFalse(delegates().containsKey(endpoint))
        assertSame(endpoint, CallbackEndpoints.wrap(stub))
        assertFalse(delegates().containsKey(endpoint))
    }

    @Test fun `handover retains platform carriers but no old stub delegate`() {
        val stub = Stub("com.hchen.superlyricapi.v4.ILyricFrameListener")
        val endpoint = CallbackEndpoints.wrap(stub)
        val state = CallbackEndpoints.retire()
        assertSame(endpoint, state["frame"])
        assertEquals(Binder::class.java, state["frame"]!!.javaClass)
        assertTrue(delegates().isEmpty())
    }
}
