package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.signal.generateLastResortPreKey
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.signal.generatePrekeys
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SQLitePreKeyPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    val keyVault = generateNewKeyVault("test")
    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var keyPersistenceManager: SQLitePreKeyPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        keyPersistenceManager = SQLitePreKeyPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    @Test
    fun `getSignedPreKey should return null when given an invalid id`() {
        assertNull(keyPersistenceManager.getSignedPreKey(1).get())
    }

    @Test
    fun `getUnsignedPreKey should return null when given an invalid id`() {
        assertNull(keyPersistenceManager.getUnsignedPreKey(1).get())
    }

    fun assertSignedPreKeyPresent(expected: SignedPreKeyRecord) {
        val got = assertNotNull(keyPersistenceManager.getSignedPreKey(expected.id).get(), "Key wasn't stored")

        //cheap way of checking for equality
        assertTrue(Arrays.equals(got.serialize(), expected.serialize()))
    }

    @Test
    fun `putGeneratedKeys should be able to store and retrieve signed prekeys`() {
        val nextPreKeyIds = keyPersistenceManager.getNextPreKeyIds().get()

        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, nextPreKeyIds.nextSignedId, nextPreKeyIds.nextUnsignedId, 2)
        keyPersistenceManager.putGeneratedPreKeys(generatedPreKeys).get()

        assertSignedPreKeyPresent(generatedPreKeys.signedPreKey)
    }

    fun assertUnsignedPreKeyPresent(expected: PreKeyRecord) {
        val id = expected.id
        val got = assertNotNull(keyPersistenceManager.getUnsignedPreKey(id).get(), "Key $id not found")
        assertTrue(Arrays.equals(got.serialize(), expected.serialize()), "Key $id was not serialized properly")

    }

    fun assertUnsignedPreKeysPresent(expected: List<PreKeyRecord>) {
        for (unsignedPreKey in expected) {
            assertUnsignedPreKeyPresent(unsignedPreKey)
        }
    }

    @Test
    fun `putGeneratedKeys should be able to store and retrieve unsigned prekeys`() {
        val nextPreKeyIds = keyPersistenceManager.getNextPreKeyIds().get()

        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, nextPreKeyIds.nextSignedId, nextPreKeyIds.nextUnsignedId, 2)
        keyPersistenceManager.putGeneratedPreKeys(generatedPreKeys).get()

        assertUnsignedPreKeysPresent(generatedPreKeys.oneTimePreKeys)
    }

    @Test
    fun `putGeneratedKeys should update existing ids`() {
        val nextPreKeyIds = keyPersistenceManager.getNextPreKeyIds().get()

        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, nextPreKeyIds.nextSignedId, nextPreKeyIds.nextUnsignedId, 2)
        val generatedPreKeysUpdate = generatePrekeys(keyVault.identityKeyPair, nextPreKeyIds.nextSignedId, nextPreKeyIds.nextUnsignedId, 2)

        keyPersistenceManager.putGeneratedPreKeys(generatedPreKeys).get()
        keyPersistenceManager.putGeneratedPreKeys(generatedPreKeysUpdate).get()

        assertSignedPreKeyPresent(generatedPreKeysUpdate.signedPreKey)
        assertUnsignedPreKeysPresent(generatedPreKeysUpdate.oneTimePreKeys)
    }

    @Test
    fun `putLastResortPreKey should store an unsigned prekey`() {
        val lastResortPreKey = generateLastResortPreKey()

        keyPersistenceManager.putLastResortPreKey(lastResortPreKey).get()
        assertUnsignedPreKeyPresent(lastResortPreKey)
    }
}