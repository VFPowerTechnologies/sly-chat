package io.slychat.messenger.core.persistence.sqlite

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter

object JSONMapper {
    @JvmStatic
    val reader: ObjectReader
    @JvmStatic
    val writer: ObjectWriter

    init {
        val mapper = ObjectMapper()

        reader = mapper.reader()
        writer = mapper.writer()
    }
}