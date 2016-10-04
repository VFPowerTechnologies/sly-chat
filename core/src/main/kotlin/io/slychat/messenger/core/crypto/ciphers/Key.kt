package io.slychat.messenger.core.crypto.ciphers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.io.IOException

class KeySerializer : JsonSerializer<Key>() {
    override fun serialize(value: Key, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeBinary(value.raw)
    }
}

//jackson won't try and decode the base64 serialization, since it doesn't know it needs to
//having an alt constructor causes mockitokotlin to try and use for creating dummy values which fails (and I don't
//wanna have to start peppering createInstance everywhere)
class KeyDeserializer : JsonDeserializer<Key>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Key {
        if (p.currentToken != JsonToken.VALUE_STRING)
            throw IOException("Expected base64 string, got ${p.currentToken}")
        return Key(p.binaryValue)
    }
}

/** Cryptographic key. */
@JsonSerialize(using = KeySerializer::class)
@JsonDeserialize(using = KeyDeserializer::class)
class Key(val raw: ByteArray)