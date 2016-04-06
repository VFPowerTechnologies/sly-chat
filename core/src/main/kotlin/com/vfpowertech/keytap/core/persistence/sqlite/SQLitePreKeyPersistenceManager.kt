package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.vfpowertech.keytap.core.crypto.LAST_RESORT_PREKEY_ID
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.persistence.PreKeyIds
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import nl.komponents.kovenant.Promise
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

class SQLitePreKeyPersistenceManager(private val sqlitePersistenceManager: SQLitePersistenceManager) : PreKeyPersistenceManager {
    override fun putLastResortPreKey(lastResortPreKey: PreKeyRecord): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        assert(lastResortPreKey.id == LAST_RESORT_PREKEY_ID)
        //should only be done once, so no need to update on conflict
        connection.prepare("INSERT INTO unsigned_prekeys (id, serialized) VALUES (?, ?)").use { stmt ->
            stmt.bind(1, lastResortPreKey.id)
            stmt.bind(2, lastResortPreKey.serialize())
            stmt.step()
        }
        Unit
    }

    private fun containsPreKey(tableName: String, id: Int): Promise<Boolean, Exception> =
        sqlitePersistenceManager.runQuery { connection ->
            connection.prepare("SELECT 1 FROM $tableName WHERE id=?").use { stmt ->
                stmt.bind(1, id)
                stmt.step()
            }
        }

    private inline fun <T> getPreKey(tableName: String, id: Int, crossinline constructor: (ByteArray) -> T): Promise<T?, Exception> =
        sqlitePersistenceManager.runQuery { connection ->
            connection.prepare("SELECT serialized FROM $tableName WHERE id=?").use { stmt ->
                stmt.bind(1, id)
                if (!stmt.step())
                    null
                else
                    constructor(stmt.columnBlob(0))
            }
        }

    private fun removePreKey(tableName: String, id: Int): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("DELETE FROM $tableName WHERE id=?").use { stmt ->
            stmt.bind(1, id)
            stmt.step()
            Unit
        }
    }

    override fun getNextPreKeyIds(): Promise<PreKeyIds, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT next_signed_id, next_unsigned_id FROM prekey_ids LIMIT 1").use { stmt ->
            stmt.step()
            PreKeyIds(
                stmt.columnInt(0),
                stmt.columnInt(1)
            )
        }
    }

    override fun getSignedPreKey(id: Int): Promise<SignedPreKeyRecord?, Exception> =
        getPreKey("signed_prekeys", id) { SignedPreKeyRecord(it) }

    override fun getUnsignedPreKey(id: Int): Promise<PreKeyRecord?, Exception> =
        getPreKey("unsigned_prekeys", id) { PreKeyRecord(it) }

    override fun getSignedPreKeys(): Promise<List<SignedPreKeyRecord>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.prepare("SELECT serialized FROM unsigned_prekeys").use { stmt ->
            stmt.map { SignedPreKeyRecord(stmt.columnBlob(0)) }
        }
    }

    //SignPreKeyRecord and PreKeyRecord have no common interface/subclass, so we need to serialize before passing this in
    private fun addPreKeyNoTransaction(connection: SQLiteConnection, tableName: String, id: Int, serialized: ByteArray) {
        connection.prepare("INSERT OR REPLACE INTO $tableName (id, serialized) VALUES (?, ?)").use { stmt ->
            stmt.bind(1, id)
            stmt.bind(2, serialized)
            stmt.step()
        }
    }

    private fun addPreKey(tableName: String, id: Int, serialized: ByteArray): Promise<Unit, Exception> =
        sqlitePersistenceManager.runQuery { addPreKeyNoTransaction(it,tableName, id, serialized) }

    override fun putGeneratedPreKeys(generatedPreKeys: GeneratedPreKeys): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            //if we've looped back around, just overwriting the previous keys
            //as the max key id is sufficiently large, this should never lead to any issues under normal circumstances
            val signedPreKey = generatedPreKeys.signedPreKey
            addPreKeyNoTransaction(connection, "signed_prekeys", signedPreKey.id, signedPreKey.serialize())

            connection.batchInsert("INSERT OR REPLACE INTO unsigned_prekeys (id, serialized) VALUES (?, ?)", generatedPreKeys.oneTimePreKeys) { stmt, unsignedPreKey ->
                stmt.bind(1, unsignedPreKey.id)
                stmt.bind(2, unsignedPreKey.serialize())
            }

            val nextSignedId = generatedPreKeys.nextSignedId()
            val nextUnsignedId = generatedPreKeys.nextUnsignedId()
            connection.exec("UPDATE prekey_ids SET next_signed_id=$nextSignedId, next_unsigned_id=$nextUnsignedId")
        }

        Unit
    }

    override fun removeSignedPreKey(id: Int): Promise<Unit, Exception> =
        removePreKey("signed_prekeys", id)

    override fun removeUnsignedPreKey(id: Int): Promise<Unit, Exception> =
        removePreKey("unsigned_prekeys", id)

    override fun containsUnsignedPreKey(id: Int): Promise<Boolean, Exception> =
        containsPreKey("unsigned_prekeys", id)

    override fun containsSignedPreKey(id: Int): Promise<Boolean, Exception> =
        containsPreKey("signed_prekeys", id)

    override fun putSignedPreKey(signedPreKey: SignedPreKeyRecord): Promise<Unit, Exception> =
        addPreKey("signed_prekeys", signedPreKey.id, signedPreKey.serialize())

    override fun putUnsignedPreKey(unsignedPreKey: PreKeyRecord): Promise<Unit, Exception> =
        addPreKey("unsigned_prekeys", unsignedPreKey.id, unsignedPreKey.serialize())
}