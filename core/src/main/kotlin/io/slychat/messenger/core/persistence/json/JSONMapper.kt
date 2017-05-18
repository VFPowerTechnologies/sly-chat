package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper

object JSONMapper {
    @JvmStatic
    val mapper: ObjectMapper = ObjectMapper()
}