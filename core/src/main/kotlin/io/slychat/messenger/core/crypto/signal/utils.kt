package io.slychat.messenger.core.crypto.signal

import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.libsignal.util.Medium
import java.security.SecureRandom

//KeyHelper caps keys at Medium.VALUE-1 (unsigned 24bit)
//the last resort prekey always has id=Medium.VALUE
//generatePreKeys handles this via modulo arithmetic, but for signed keys we need to do it ourselves
private val MIN_PRE_KEY_ID = 1
private val MAX_PRE_KEY_ID = Medium.MAX_VALUE-1

val LAST_RESORT_PREKEY_ID = Medium.MAX_VALUE

/** Returns a random prekey id. */
fun randomPreKeyId(): Int {
    val rand = SecureRandom()
    return MIN_PRE_KEY_ID + rand.nextInt((MAX_PRE_KEY_ID - MIN_PRE_KEY_ID) + 1)
}

/** Returns the next available id. On overflow, loops back to the min value. */
fun nextPreKeyId(current: Int): Int {
    if (current == MAX_PRE_KEY_ID)
        return MIN_PRE_KEY_ID
    else
        return current + 1
}

/** Generates a last resort prekey. Should only be generated once. This key will always have id set to Medium.MAX_VALUE. */
fun generateLastResortPreKey(): PreKeyRecord =
    KeyHelper.generateLastResortPreKey()

/** Generate a new batch of prekeys */
fun generatePrekeys(identityKeyPair: IdentityKeyPair, nextSignedPreKeyId: Int, nextPreKeyId: Int, count: Int): GeneratedPreKeys {
    io.slychat.messenger.core.require(nextSignedPreKeyId > 0, "nextSignedPreKeyId must be > 0")
    io.slychat.messenger.core.require(nextPreKeyId > 0, "nextPreKeyId must be > 0")
    io.slychat.messenger.core.require(count > 0, "count must be > 0")

    val signedPrekey = KeyHelper.generateSignedPreKey(identityKeyPair, nextSignedPreKeyId)
    val oneTimePreKeys = KeyHelper.generatePreKeys(nextPreKeyId, count)

    return GeneratedPreKeys(signedPrekey, oneTimePreKeys)
}

/** Add the prekeys into the given store */
fun addPreKeysToStore(signalStore: SignalProtocolStore, generatedPreKeys: GeneratedPreKeys) {
    signalStore.storeSignedPreKey(generatedPreKeys.signedPreKey.id, generatedPreKeys.signedPreKey)

    for (k in generatedPreKeys.oneTimePreKeys)
        signalStore.storePreKey(k.id, k)
}

/** Should only be done once. */
fun addLastResortPreKeyToStore(signalStore: SignalProtocolStore, lastResortPreKey: PreKeyRecord) {
    signalStore.storePreKey(lastResortPreKey.id, lastResortPreKey)
}