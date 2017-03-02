package io.slychat.messenger.core.crypto.ciphers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize

class CipherIdSerializer : JsonSerializer<CipherId>() {
    override fun serialize(value: CipherId, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeNumber(value.short)
    }
}

@JsonSerialize(using = CipherIdSerializer::class)
data class CipherId(val short: Short) {
    init {
        require(short in 0..255) { "id must be 0 <= id <= 255, got $short" }
    }

    constructor(i: Int) : this(i.toShort())

    override fun toString(): String {
        return short.toString()
    }
}