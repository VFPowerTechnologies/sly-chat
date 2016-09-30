package io.slychat.messenger.core.crypto.ciphers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize

class KeySerializer : JsonSerializer<Key>() {
    override fun serialize(value: Key, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeBinary(value.raw)
    }
}

/** Cryptographic key. */
@JsonSerialize(using = KeySerializer::class)
class Key(val raw: ByteArray)