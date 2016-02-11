package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLite
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import com.vfpowertech.keytap.core.loadSharedLibFromResource
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SQLiteKeyPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteKeyPersistenceManager::class.java.loadSharedLibFromResource("sqlite4java-linux-amd64-1.0.392")
            SQLite.loadLibrary()
        }
    }


    val keyVault = generateNewKeyVault("test")
    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var keyPersistenceManager: SQLiteKeyPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, ByteArray(0), null)
        persistenceManager.init()
        keyPersistenceManager = SQLiteKeyPersistenceManager(persistenceManager)
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
}