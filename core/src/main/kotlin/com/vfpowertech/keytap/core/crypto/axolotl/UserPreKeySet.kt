package com.vfpowertech.keytap.core.crypto.axolotl

import org.whispersystems.libaxolotl.IdentityKey
import org.whispersystems.libaxolotl.state.PreKeyRecord
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord

/** A user's single one-time prekey, retrieved from a server. */
data class UserPreKeySet(
    val identityKey: IdentityKey,
    val signedPreKey: SignedPreKeyRecord,
    val oneTimePreKey: PreKeyRecord
)
