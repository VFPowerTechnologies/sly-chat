package com.vfpowertech.keytap.core.crypto.axolotl

import org.whispersystems.libaxolotl.state.PreKeyRecord
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord

/** A batch of generated prekeys */
data class GeneratedPreKeys(
    val signedPreKey: SignedPreKeyRecord,
    val oneTimePreKeys: List<PreKeyRecord>,
    val lastResortPreKey: PreKeyRecord)
