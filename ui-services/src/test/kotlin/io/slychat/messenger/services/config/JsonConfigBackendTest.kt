package io.slychat.messenger.services.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.withKovenantThreadedContext
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class JsonConfigBackendTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenentTestMode = KovenantTestModeRule()
    }

    data class Config(val i: Int = 0)

    val configObject = Config()

    val configName = "test-config"

    lateinit var configStorage: ConfigStorage

    @Before
    fun before() {
        configStorage = mock<ConfigStorage>()
    }

    @Test
    fun `it should call storage when update is called`() {
        val configBackend = JsonConfigBackend(configName, configStorage)

        configBackend.update(configObject)

        verify(configStorage).write(any())
    }

    @Test
    fun `it should call storage when reading`() {
        val configBackend = JsonConfigBackend(configName, configStorage)

        val bytes = ObjectMapper().writeValueAsBytes(configObject)

        whenever(configStorage.read()).thenReturn(bytes)

        val got = configBackend.read(Config::class.java).get()

        assertEquals(configObject, got, "Invalid config object")
    }

    //this is really hacky; not really sure how the heck to actually test this in a sane manner
    @Test
    fun `it should queue writing if update is called while writing`() {
        val v = AtomicInteger()

        whenever(configStorage.write(any())).thenAnswer {
            Thread.sleep(50)
            v.getAndIncrement()
        }

        val configBackend = JsonConfigBackend(configName, configStorage)

        withKovenantThreadedContext {
            (0..4).forEach {
                configBackend.update(configObject)
            }

            Thread.sleep(350)
            assertEquals(2, v.get(), "Invalid write call count")
        }
    }
}