package io.slychat.messenger.core.crypto.signal

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.PreKeyPersistenceManager
import io.slychat.messenger.core.persistence.SignalSessionPersistenceManager
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.InvalidKeyIdException
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord

class SQLiteSignalProtocolStore(
    private val identityKeyPair: IdentityKeyPair,
    private val registrationId: Int,
    private val signalSessionPersistenceManager: SignalSessionPersistenceManager,
    private val preKeyPersistenceManager: PreKeyPersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager
) : SignalProtocolStore {
    override fun containsSession(address: SignalProtocolAddress): Boolean =
        signalSessionPersistenceManager.containsSession(address).get()

    override fun deleteSession(address: SignalProtocolAddress) =
        signalSessionPersistenceManager.deleteSession(address).get()

    override fun getSubDeviceSessions(name: String): List<Int> =
        signalSessionPersistenceManager.getSubDeviceSessions(name).get()

    override fun deleteAllSessions(name: String) =
        signalSessionPersistenceManager.deleteAllSessions(name).get()

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? =
        signalSessionPersistenceManager.loadSession(address).get()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) =
        signalSessionPersistenceManager.storeSession(address, record).get()

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        preKeyPersistenceManager.removeSignedPreKey(signedPreKeyId).get()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return preKeyPersistenceManager.containsSignedPreKey(signedPreKeyId).get()
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        preKeyPersistenceManager.putSignedPreKey(record).get()
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        return preKeyPersistenceManager.getSignedPreKey(signedPreKeyId).get() ?: throw InvalidKeyIdException("Invalid signed prekey id: $signedPreKeyId")
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return preKeyPersistenceManager.getSignedPreKeys().get()
    }

    override fun removePreKey(preKeyId: Int) {
        preKeyPersistenceManager.removeUnsignedPreKey(preKeyId).get()
    }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return preKeyPersistenceManager.getUnsignedPreKey(preKeyId).get() ?: throw InvalidKeyIdException("Invalid prekey id: $preKeyId")
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
        return identityKeyPair
    }

    override fun isTrustedIdentity(name: String, identityKey: IdentityKey): Boolean {
        //don't trust anyone we haven't yet added to the contact list
        val contact = contactsPersistenceManager.get(UserId(name.toLong())).get() ?: return false
        return contact.publicKey == identityKey.serialize().hexify()
    }

    override fun getLocalRegistrationId(): Int {
        return registrationId
    }
}