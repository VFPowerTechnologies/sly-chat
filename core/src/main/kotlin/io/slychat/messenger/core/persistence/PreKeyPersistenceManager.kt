package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.crypto.signal.GeneratedPreKeys
import nl.komponents.kovenant.Promise
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

/** Responsible for persisting PreKeys and associated data. */
interface PreKeyPersistenceManager {
    /** Stores the given prekeys, as well as updates the next available IDs. */
    fun putGeneratedPreKeys(generatedPreKeys: GeneratedPreKeys): Promise<Unit, Exception>
    fun putLastResortPreKey(lastResortPreKey: PreKeyRecord): Promise<Unit, Exception>
    fun getSignedPreKey(id: Int): Promise<SignedPreKeyRecord?, Exception>
    fun getUnsignedPreKey(id: Int): Promise<PreKeyRecord?, Exception>
    fun getNextPreKeyIds(): Promise<PreKeyIds, Exception>
    fun getSignedPreKeys(): Promise<List<SignedPreKeyRecord>, Exception>
    fun removeSignedPreKey(id: Int): Promise<Unit, Exception>
    fun removeUnsignedPreKey(id: Int): Promise<Unit, Exception>
    /** Removes unsigned prekeys in the range [start, end). */
    fun removeUnsignedPreKeyRange(start: Int, end: Int): Promise<Unit, Exception>
    fun containsUnsignedPreKey(id: Int): Promise<Boolean, Exception>
    fun containsSignedPreKey(id: Int): Promise<Boolean, Exception>
    fun putSignedPreKey(signedPreKey: SignedPreKeyRecord): Promise<Unit, Exception>
    fun putUnsignedPreKey(unsignedPreKey: PreKeyRecord): Promise<Unit, Exception>
}
