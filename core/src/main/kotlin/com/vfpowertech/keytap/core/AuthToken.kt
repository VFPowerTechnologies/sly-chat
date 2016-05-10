package com.vfpowertech.keytap.core

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize

class AuthTokenSerializer : JsonSerializer<AuthToken>() {
    override fun serialize(value: AuthToken, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.string)
    }
}

@JsonSerialize(using = AuthTokenSerializer::class)
data class AuthToken(val string: String)