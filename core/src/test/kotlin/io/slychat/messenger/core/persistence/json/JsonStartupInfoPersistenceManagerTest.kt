package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.crypto.generateKey
import io.slychat.messenger.core.persistence.StartupInfo
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.testutils.withTempFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JsonStartupInfoPersistenceManagerTest {
    companion object {
        private val key = generateKey(256)
    }

    @Test
    fun `it should be able to decrypt what it encrypts`() {
        withTempFile { path ->
            val persistenceManager = JsonStartupInfoPersistenceManager(path, key)

            val startupInfo = StartupInfo(randomUserId(), "password")

            persistenceManager.store(startupInfo).get()

            val got = assertNotNull(persistenceManager.retrieve().get(), "Read failed")

            assertEquals(startupInfo, got, "Disk version doesn't match expected")
        }
    }
}