package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.HKDFInfoList
import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.randomQuota
import io.slychat.messenger.testutils.randomString
import io.slychat.messenger.testutils.withTempFile
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JSONQuotaPersistenceManagerTest {
    companion object {
        private val keySpec = DerivedKeySpec(generateKey(128), HKDFInfoList.localData())
    }

    @Test
    fun `retrieve should read back stored cache data`() {
        withTempFile {
            val manager = JSONQuotaPersistenceManager(it, keySpec)

            val quota = randomQuota()

            manager.store(quota)

            assertEquals(quota, manager.retrieve(), "Read failed")
        }
    }

    @Test
    fun `retrieve should return null if the given path doesn't exist`() {
        val manager = JSONQuotaPersistenceManager(File("/" + randomString(10)), keySpec)

        assertNull(manager.retrieve())
    }
    
    @Test
    fun `retrieve should return null if the data at the given path is corrupted`() {
        withTempFile {
            val manager = JSONQuotaPersistenceManager(it, keySpec)
            it.writeText(randomString(5))

            assertNull(manager.retrieve())
        }
    }
    
    @Test
    fun `retrieve should return null if the file is empty`() {
        withTempFile {
            val manager = JSONQuotaPersistenceManager(it, keySpec)
            it.writeBytes(emptyByteArray())

            assertNull(manager.retrieve())
        }
    }
}