package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.slychat.messenger.core.crypto.isValidUUIDFormat

class GroupIdSerializer : JsonSerializer<GroupId>() {
    override fun serialize(value: GroupId, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.string)
    }
}

@JsonSerialize(using = GroupIdSerializer::class)
data class GroupId(val string: String) {
    init {
        require(isValidUUIDFormat(string)) { "$string is not a valid GroupId" }
    }

    override fun toString(): String = string
}