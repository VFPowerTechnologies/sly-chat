package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.crypto.axolotl.GeneratedPreKeys
import nl.komponents.kovenant.Promise
import org.whispersystems.libaxolotl.state.PreKeyRecord
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord

/** Responsible for persisting PreKeys and associated data. */
interface PreKeyPersistenceManager {
    /** Stores the given prekeys, as well as updates the next available IDs. */
    fun putGeneratedPreKeys(generatedPreKeys: GeneratedPreKeys): Promise<Unit, Exception>
    fun getSignedPreKey(id: Int): Promise<SignedPreKeyRecord?, Exception>
    fun getUnsignedPreKey(id: Int): Promise<PreKeyRecord?, Exception>
    fun getNextPreKeyIds(): Promise<PreKeyIds, Exception>
}
