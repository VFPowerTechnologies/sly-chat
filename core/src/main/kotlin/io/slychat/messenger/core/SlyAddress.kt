package io.slychat.messenger.core

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.whispersystems.libsignal.SignalProtocolAddress
import java.io.IOException

val ADDRESS_USERID_DEVICEID_DELIMITER = "."

class SlyAddressDeserializer : JsonDeserializer<SlyAddress>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SlyAddress {
        val text = p.text

        if (p.currentToken != JsonToken.VALUE_STRING)
            throw IOException("Expected VALUE_STRING, got ${p.currentToken} for $text")

        val parts = text.split(ADDRESS_USERID_DEVICEID_DELIMITER, limit = 2)
        if (parts.size != 2)
            throw IOException("Invalid address format: $text")

        try {
            return SlyAddress(UserId(parts[0].toLong()), parts[1].toInt())
        }
        catch (e: NumberFormatException) {
            throw IOException("Invalid address format: $text", e)
        }
    }
}

class SlyAddressSerializer : JsonSerializer<SlyAddress>() {
    override fun serialize(value: SlyAddress, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.asString())
    }
}

class SlyAddressKeySerializer : JsonSerializer<SlyAddress>() {
    override fun serialize(value: SlyAddress, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeFieldName(value.asString())
    }
}

class SlyAddressKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): Any {
        return SlyAddress.fromString(key)!!
    }
}

@JsonSerialize(using = SlyAddressSerializer::class)
@JsonDeserialize(using = SlyAddressDeserializer::class)
data class SlyAddress(val id: UserId, val deviceId: Int) {
    fun toSignalAddress(): SignalProtocolAddress = SignalProtocolAddress(id.long.toString(), deviceId)

    /** Returns the address serialized as a string. Function name choosen to not conflict with toString. */
    fun asString(): String = id.long.toString() + ADDRESS_USERID_DEVICEID_DELIMITER + deviceId.toString()

    companion object {
        fun fromString(s: String): SlyAddress? {
            val parts = s.split(ADDRESS_USERID_DEVICEID_DELIMITER, limit = 2)
            if (parts.size != 2)
                return null

            try {
                val userId = parts[0].toLong()
                val deviceId = parts[1].toInt()
                return SlyAddress(UserId(userId), deviceId)
            }
            catch (e: NumberFormatException) {
                return null
            }
        }
    }
}