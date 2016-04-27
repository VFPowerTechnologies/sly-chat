package com.vfpowertech.keytap.core

import org.whispersystems.libsignal.SignalProtocolAddress

class KeyTapAddress(val id: UserId, val deviceId: Int) {
    fun toSignalAddress(): SignalProtocolAddress = SignalProtocolAddress(id.id.toString(), deviceId)

    /** Returns the address serialized as a string. Function name choosen to not conflict with toString. */
    fun asString(): String = "${id.id}:$deviceId"

    companion object {
        fun fromString(s: String): KeyTapAddress? {
            val parts = s.split('.', limit = 2)
            if (parts.size != 2)
                return null

            try {
                val userId = parts[0].toLong()
                val deviceId = parts[1].toInt()
                return KeyTapAddress(UserId(userId), deviceId)
            }
            catch (e: NumberFormatException) {
                return null
            }
        }
    }
}