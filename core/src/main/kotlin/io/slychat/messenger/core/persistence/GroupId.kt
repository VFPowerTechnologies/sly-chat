package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize

class GroupIdSerializer : JsonSerializer<GroupId>() {
    override fun serialize(value: GroupId, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.string)
    }
}

@JsonSerialize(using = GroupIdSerializer::class)
data class GroupId(val string: String) {
    override fun toString(): String = string
}