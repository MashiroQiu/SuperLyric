package com.hchen.superlyric.reload

import java.util.concurrent.ExecutorService
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReloadSdkContractTest {
    private fun type(name: String) = Class.forName(name, false, javaClass.classLoader)

    @Test fun `pinned SDK retirement fields and methods exist`() {
        assertTrue(ExecutorService::class.java.isAssignableFrom(type("com.hchen.superlyricapi.internal.Background").getDeclaredField("executor").type))
        assertNotNull(type("com.hchen.superlyricapi.internal.BinderHub").getDeclaredMethod("reset"))
        assertEquals(CoroutineScope::class.java, type("io.github.proify.lyricon.provider.impl.ProviderV27Impl").getDeclaredField("coroutineScope").type)
        assertEquals(CoroutineScope::class.java, type("io.github.proify.lyricon.provider.impl.RemoteServiceProxy").getDeclaredField("callbackScope").type)
        assertNotNull(type("io.github.proify.lyricon.provider.CentralServiceReceiver").getDeclaredField("innerReceiver"))
        for (field in listOf("providerBinder", "localProviderService")) assertNotNull(type("io.github.proify.lyricon.provider.impl.ProviderV27Impl").getDeclaredField(field))
        for (field in listOf("iRemoteService", "deathRecipient")) assertNotNull(type("io.github.proify.lyricon.provider.impl.RemoteServiceProxy").getDeclaredField(field))
    }

    @Test fun `all exported callback stubs have replaceable binder methods`() {
        for (name in listOf("com.hchen.superlyricapi.v4.ILyricFrameListener", "com.hchen.superlyricapi.ISuperLyricReceiver",
            "io.github.proify.lyricon.provider.IProviderBinder", "io.github.proify.lyricon.provider.IProviderService")) {
            assertNotNull(type("$name\$Stub").getDeclaredMethod("asBinder"))
        }
    }
}
