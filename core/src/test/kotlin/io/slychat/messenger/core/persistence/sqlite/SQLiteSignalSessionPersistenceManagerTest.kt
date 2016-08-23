package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.randomContactInfo
import io.slychat.messenger.core.randomSignalAddress
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SessionState
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SQLiteSignalSessionPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    fun randomSessionRecord(): SessionRecord = SessionRecord(SessionState())

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var signalSessionsPersistenceManager: SQLiteSignalSessionPersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        signalSessionsPersistenceManager = SQLiteSignalSessionPersistenceManager(persistenceManager)
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
    }

    fun insertRandomContact(): UserId {
        val contactInfo = randomContactInfo()

        contactsPersistenceManager.add(contactInfo).get()

        return contactInfo.id
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    //SessionRecord has no equals
    fun assertSessionsEqual(a: SessionRecord, b: SessionRecord) {
        assertTrue(Arrays.equals(a.serialize(), b.serialize()), "Sessions don't match")
    }

    @Test
    fun `loadSession should return a stored sessioned`() {
        val userId = insertRandomContact()
        val address = randomSignalAddress(userId)

        val original = randomSessionRecord()
        signalSessionsPersistenceManager.storeSession(address, original).get()

        val loaded = assertNotNull(signalSessionsPersistenceManager.loadSession(address).get(), "Session not stored")

        assertSessionsEqual(original, loaded)
    }

    @Test
    fun `getSubDeviceSessions should return added sessions`() {
        val userId = insertRandomContact()
        val address = randomSignalAddress(userId)
        val address2 = randomSignalAddress(userId)

        signalSessionsPersistenceManager.storeSession(address, randomSessionRecord()).get()
        signalSessionsPersistenceManager.storeSession(address2, randomSessionRecord()).get()

        val subDevices = signalSessionsPersistenceManager.getSubDeviceSessions(address.name).get()

        Assertions.assertThat(subDevices).containsOnly(address.deviceId, address2.deviceId)
    }
}