package io.slychat.messenger.core.crypto.ciphers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.spongycastle.util.encoders.Base64

class KeySerializer : JsonSerializer<Key>() {
    override fun serialize(value: Key, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeBinary(value.raw)
    }
}

/** Cryptographic key. */
@JsonSerialize(using = KeySerializer::class)
class Key(val raw: ByteArray) {
    //jackson won't try and decode the base64 serialization, since it doesn't know it needs to
    //this alt constructor does the work without having to create a JsonDeserializer
    constructor(base64: String) : this(Base64.decode(base64))
}