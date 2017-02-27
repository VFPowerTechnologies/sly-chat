package io.slychat.messenger.core.persistence.sqlite

import com.fasterxml.jackson.databind.ObjectMapper

object JSONMapper {
    @JvmStatic
    val mapper: ObjectMapper = ObjectMapper()
}