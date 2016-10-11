package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.slychat.messenger.core.crypto.isValidUUIDFormat

class MessageIdSerializer : JsonSerializer<MessageId>() {
    override fun serialize(value: MessageId, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.string)
    }
}

//right now mockito-kotlin randomly fails tests (generally only when running via gradle) even if I register instance creators
//no idea what the deal is, so we only use this in one place currently until I get time to try and debug it; seems it doesn't
//find the existing instance creator for some reason under certain circumstances
@JsonSerialize(using = MessageIdSerializer::class)
data class MessageId(val string: String) {
    init {
        require(isValidUUIDFormat(string)) { "$string is not a valid MessageId" }
    }

    override fun toString(): String = string
}