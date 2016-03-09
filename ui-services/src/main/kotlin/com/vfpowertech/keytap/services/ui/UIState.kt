package com.vfpowertech.keytap.services.ui

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize

class UIStateDeserializer : JsonDeserializer<UIState>() {
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): UIState {
        val node: TreeNode = jp.codec.readTree(jp)
        return UIState(node.toString())
    }
}

class UIStateSerializer : JsonSerializer<UIState>() {
    override fun serialize(value: UIState, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeRaw(value.s)
    }
}

/** Opaque UI state. Contains the JSON value as-is. */
@JsonDeserialize(using = UIStateDeserializer::class)
@JsonSerialize(using = UIStateSerializer::class)
data class UIState(val s: String)