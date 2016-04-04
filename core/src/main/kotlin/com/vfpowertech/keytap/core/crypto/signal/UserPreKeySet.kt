package com.vfpowertech.keytap.core.crypto.signal

import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

/** A user's single one-time prekey, retrieved from a server. */
data class UserPreKeySet(
    val identityKey: IdentityKey,
    val signedPreKey: SignedPreKeyRecord,
    val oneTimePreKey: PreKeyRecord
)
