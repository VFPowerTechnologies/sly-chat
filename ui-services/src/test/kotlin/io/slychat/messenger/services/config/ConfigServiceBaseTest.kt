package io.slychat.messenger.services.config

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import nl.komponents.kovenant.Context
import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.testMode
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ConfigServiceBaseTest {
    companion object {
        var savedContext: Context? = null

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            savedContext = Kovenant.context
            Kovenant.testMode()
        }

        @JvmStatic
        @AfterClass
        fun afterClass() {
            val context = savedContext
            if (context != null) {
                Kovenant.context = context
                savedContext = null
            }
        }
    }

    data class TestConfig(val i: Int = 1, val j: Int = 2) {
        companion object {
            val I = "i"
            val J = "j"
        }
    }

    class TestEditorInterface(override var config: TestConfig) : ConfigServiceBase.EditorInterface<TestConfig> {
        override val modifiedKeys = HashSet<String>()

        var i: Int
            get() = config.i
            set(value) {
                modifiedKeys.add(TestConfig.I)
                config = config.copy(i = value)
            }

        var j: Int
            get() = config.j
            set(value) {
                modifiedKeys.add(TestConfig.J)
                config = config.copy(j = value)
            }
    }

    class TestConfigService(
        backend: ConfigBackend,
        override var config: TestConfig
    ) : ConfigServiceBase<TestConfig, TestEditorInterface>(backend) {
        override val configClass: Class<TestConfig> = TestConfig::class.java

        override fun makeEditor(): TestEditorInterface = TestEditorInterface(config)

        val i: Int
            get() = config.i

        val j: Int
            get() = config.j
    }

    val defaultConfig = TestConfig()

    lateinit var backend: ConfigBackend

    fun setMockConfig(testConfig: TestConfig?) {
        whenever(backend.read(TestConfig::class.java)).thenReturn(Promise.ofSuccess(testConfig))
    }

    fun testService(testConfig: TestConfig = defaultConfig): TestConfigService {
        return TestConfigService(backend, testConfig)
    }

    @Before
    fun before() {
        backend = mock<ConfigBackend>()
    }

    @Test
    fun `init should set config when one is returned`() {
        val config = TestConfig(5, 6)
        setMockConfig(config)

        val service = testService()

        service.init()

        assertEquals(config.i, service.i, "Invalid i")
        assertEquals(config.j, service.j, "Invalid j")
    }

    @Test
    fun `init should do nothing when no config is returned`() {
        setMockConfig(null)

        val service = testService()

        service.init()

        assertEquals(defaultConfig.i, service.i, "Invalid i")
        assertEquals(defaultConfig.j, service.j, "Invalid j")
    }

    @Test
    fun `withEditor should do nothing if no modifications are returned`() {
        val service = testService()

        service.withEditor {  }

        verifyZeroInteractions(backend)
    }

    @Test
    fun `withEditor should not call backend if values have not changed`() {
        val service = testService()

        service.withEditor {
            i = defaultConfig.i
            j = defaultConfig.j
        }

        verifyZeroInteractions(backend)
    }

    @Test
    fun `withEditor should apply the given modifications`() {
        val service = testService()
        val newI = service.i + 1
        val newJ = service.j + 1

        service.withEditor {
            i = newI
            j = newJ
        }

        assertEquals(newI, service.i, "Invalid i")
        assertEquals(newJ, service.j, "Invalid j")
    }

    @Test
    fun `withEditor should emit events for all changed settings`() {
        val service = testService()
        val expectedKeys = listOf(TestConfig.I, TestConfig.J).sorted()
        var receivedKeys = emptyList<String>()

        service.updates.subscribe { keys ->
            receivedKeys = keys.toList().sorted()
        }

        service.withEditor {
            i += 1
            j += 1
        }

        assertEquals(expectedKeys, receivedKeys, "Invalid setting keys")
    }

    @Test
    fun `withEditor should emit events only after applying modifications`() {
        val service = testService()

        val newI = service.i + 1
        val newJ = service.j + 1
        val expected = mapOf<String, Any>(TestConfig.I to newI, TestConfig.J to newJ)
        val received = HashMap<String, Any>()

        service.updates.subscribe { keys ->
            keys.forEach {
                when (it) {
                    TestConfig.I -> received[it] = service.i
                    TestConfig.J -> received[it] = service.j
                    else -> throw IllegalArgumentException("Unknown setting key: $it")
                }
            }
        }

        service.withEditor {
            i = newI
            j = newJ
        }

        assertEquals(expected, received, "Invalid setting values")
    }
}