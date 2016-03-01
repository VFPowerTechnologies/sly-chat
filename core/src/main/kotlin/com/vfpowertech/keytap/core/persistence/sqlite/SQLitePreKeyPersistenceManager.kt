package com.vfpowertech.keytap.core.persistence.sqlite

import com.vfpowertech.keytap.core.crypto.axolotl.GeneratedPreKeys
import com.vfpowertech.keytap.core.persistence.PreKeyIds
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import nl.komponents.kovenant.Promise
import org.whispersystems.libaxolotl.state.PreKeyRecord
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord

class SQLitePreKeyPersistenceManager(private val sqlitePersistenceManager: SQLitePersistenceManager) : PreKeyPersistenceManager {
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

    override fun putGeneratedPreKeys(generatedPreKeys: GeneratedPreKeys): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            connection.prepare("INSERT INTO signed_prekeys (id, serialized) VALUES (?, ?)").use { stmt ->
                val signedPreKey = generatedPreKeys.signedPreKey
                stmt.bind(1, signedPreKey.id)
                stmt.bind(2, signedPreKey.serialize())
                stmt.step()
            }

            connection.batchInsert("INSERT INTO unsigned_prekeys (id, serialized) VALUES (?, ?)", generatedPreKeys.oneTimePreKeys) { stmt, unsignedPreKey ->
                stmt.bind(1, unsignedPreKey.id)
                stmt.bind(2, unsignedPreKey.serialize())
            }

            val nextSignedId = generatedPreKeys.nextSignedId()
            val nextUnsignedId = generatedPreKeys.nextUnsignedId()
            connection.exec("UPDATE prekey_ids SET next_signed_id=$nextSignedId, next_unsigned_id=$nextUnsignedId")
        }

        Unit
    }
}