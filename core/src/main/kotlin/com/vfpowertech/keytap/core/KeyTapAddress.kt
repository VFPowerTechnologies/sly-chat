package com.vfpowertech.keytap.core

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.whispersystems.libsignal.SignalProtocolAddress
import java.io.IOException

class KeyTapAddressDeserializer : JsonDeserializer<KeyTapAddress>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): KeyTapAddress {
        val text = p.text

        if (p.currentToken != JsonToken.VALUE_STRING)
            throw IOException("Expected VALUE_STRING, got ${p.currentToken} for $text")

        val parts = text.split(":", limit = 2)
        if (parts.size != 2)
            throw IOException("Invalid address format: $text")

        try {
            return KeyTapAddress(UserId(parts[0].toLong()), parts[1].toInt())
        }
        catch (e: NumberFormatException) {
            throw IOException("Invalid address format: $text", e)
        }
    }
}

@JsonDeserialize(using = KeyTapAddressDeserializer::class)
class KeyTapAddress(val id: UserId, val deviceId: Int) {
    fun toSignalAddress(): SignalProtocolAddress = SignalProtocolAddress(id.long.toString(), deviceId)

    /** Returns the address serialized as a string. Function name choosen to not conflict with toString. */
    fun asString(): String = "${id.long}:$deviceId"

    companion object {
        fun fromString(s: String): KeyTapAddress? {
            val parts = s.split(':', limit = 2)
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