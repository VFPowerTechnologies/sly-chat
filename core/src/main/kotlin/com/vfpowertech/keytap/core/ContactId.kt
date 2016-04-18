package com.vfpowertech.keytap.core

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize

class UserIdSerializer : JsonSerializer<UserId>() {
    override fun serialize(value: UserId, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeNumber(value.id)
    }
}

/** Represents a user's ID. */
@JsonSerialize(using = UserIdSerializer::class)
data class UserId(val id: Long)