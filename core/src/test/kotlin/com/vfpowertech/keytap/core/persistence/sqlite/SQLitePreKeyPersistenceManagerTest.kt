package com.vfpowertech.keytap.core.persistence.sqlite

import com.vfpowertech.keytap.core.crypto.generateLastResortPreKey
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
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
        persistenceManager = SQLitePersistenceManager(null, ByteArray(0), null)
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

    @Test
    fun `putGeneratedKeys should be able to store and retrieve signed prekeys`() {
        val nextPreKeyIds = keyPersistenceManager.getNextPreKeyIds().get()

        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, nextPreKeyIds.nextSignedId, nextPreKeyIds.nextUnsignedId, 2)
        keyPersistenceManager.putGeneratedPreKeys(generatedPreKeys).get()

        val got = keyPersistenceManager.getSignedPreKey(generatedPreKeys.signedPreKey.id).get()
        assertNotNull(got, "Key wasn't stored")

        //cheap way of checking for equality
        assertTrue(Arrays.equals(got!!.serialize(), generatedPreKeys.signedPreKey.serialize()))
    }

    @Test
    fun `putGeneratedKeys should be able to store and retrieve unsigned prekeys`() {
        val nextPreKeyIds = keyPersistenceManager.getNextPreKeyIds().get()

        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, nextPreKeyIds.nextSignedId, nextPreKeyIds.nextUnsignedId, 2)
        keyPersistenceManager.putGeneratedPreKeys(generatedPreKeys).get()

        for (unsignedPreKey in generatedPreKeys.oneTimePreKeys) {
            val id = unsignedPreKey.id
            val got = keyPersistenceManager.getUnsignedPreKey(id).get()
            assertNotNull(got, "Key $id not found")
            assertTrue(Arrays.equals(got!!.serialize(), unsignedPreKey.serialize()), "Key $id was not serialized properly")
        }
    }

    @Test
    fun `putLastResortPreKey should store an unsigned prekey`() {
        val lastResortPreKey = generateLastResortPreKey()

        keyPersistenceManager.putLastResortPreKey(lastResortPreKey).get()
        val got = keyPersistenceManager.getUnsignedPreKey(lastResortPreKey.id).get()
        assertNotNull(got)
        assertTrue(Arrays.equals(got!!.serialize(), lastResortPreKey.serialize()), "Key was not serialized properly")
    }
}