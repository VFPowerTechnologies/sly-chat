package com.vfpowertech.keytap.services.crypto

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.core.persistence.sqlite.use
import com.vfpowertech.keytap.services.UserLoginData
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord

class SQLiteSignalProtocolStore(
    private val userLoginData: UserLoginData,
    private val sqlitePersistenceManager: SQLitePersistenceManager,
    private val preKeyPersistenceManager: PreKeyPersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager
) : SignalProtocolStore {
    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return sqlitePersistenceManager.syncRunQuery { connection ->
            connection.prepare("SELECT 1 FROM signal_sessions WHERE address=?").use { stmt ->
                stmt.bind(1, signalAddressToString(address))
                stmt.step()
            }
        }
    }

    private fun signalAddressToString(address: SignalProtocolAddress): String =
        "${address.name}:${address.deviceId}"

    override fun deleteSession(address: SignalProtocolAddress) {
        sqlitePersistenceManager.syncRunQuery { connection ->
            connection.prepare("DELETE FROM signal_sessions WHERE address=?").use { stmt ->
                stmt.bind(1, signalAddressToString(address))
                stmt.step()
            }
        }
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        throw UnsupportedOperationException()
    }

    override fun deleteAllSessions(name: String) {
        sqlitePersistenceManager.syncRunQuery { connection ->
            connection.exec("DELETE FROM signal_sessions")
        }
    }

    //TODO version upgrade
    override fun loadSession(address: SignalProtocolAddress): SessionRecord? = sqlitePersistenceManager.syncRunQuery { connection ->
        connection.prepare("SELECT session FROM signal_sessions WHERE address=?").use { stmt ->
            stmt.bind(1, signalAddressToString(address))
            if (!stmt.step())
                SessionRecord()
            else {
                val serialized = stmt.columnBlob(0)
                SessionRecord(serialized)
            }
        }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        sqlitePersistenceManager.syncRunQuery { connection ->
            connection.prepare("INSERT OR REPLACE INTO signal_sessions (address, session) VALUES (?, ?)").use { stmt ->
                stmt.bind(1, signalAddressToString(address))
                stmt.bind(2, record.serialize())
                stmt.step()
            }
        }
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        preKeyPersistenceManager.removeSignedPreKey(signedPreKeyId).get()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return preKeyPersistenceManager.containsSignedPreKey(signedPreKeyId).get()
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        preKeyPersistenceManager.putSignedPreKey(record).get()
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord? {
        return preKeyPersistenceManager.getSignedPreKey(signedPreKeyId).get()
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return preKeyPersistenceManager.getSignedPreKeys().get()
    }

    override fun removePreKey(preKeyId: Int) {
        preKeyPersistenceManager.removeUnsignedPreKey(preKeyId).get()
    }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord? {
        return preKeyPersistenceManager.getUnsignedPreKey(preKeyId).get()
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        preKeyPersistenceManager.putUnsignedPreKey(record).get()
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return preKeyPersistenceManager.containsUnsignedPreKey(preKeyId).get()
    }

    override fun saveIdentity(name: String, identityKey: IdentityKey) {
        //the identity keys are static per account; so we do nothing here, as simply adding a contact records their pubkey
    }

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return userLoginData.keyVault.identityKeyPair
    }

    override fun isTrustedIdentity(name: String, identityKey: IdentityKey): Boolean {
        //don't trust anyone we haven't yet added to the contact list
        val contact = contactsPersistenceManager.get(UserId(name.toLong())).get() ?: return false
        return contact.publicKey == identityKey.serialize().hexify()
    }

    //TODO nfi what this is used for; always before saveIdentity, after getIdentityKeyPair
    override fun getLocalRegistrationId(): Int {
        return 0
    }
}