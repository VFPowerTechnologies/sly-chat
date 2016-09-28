package io.slychat.messenger.core.crypto.signal

import org.whispersystems.libsignal.util.Medium
import java.security.SecureRandom

//KeyHelper caps keys at Medium.VALUE-1 (unsigned 24bit)
//the last resort prekey always has id=Medium.VALUE
//generatePreKeys handles this via modulo arithmetic, but for signed keys we need to do it ourselves
private val MIN_PRE_KEY_ID = 1
private val MAX_PRE_KEY_ID = Medium.MAX_VALUE-1

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

