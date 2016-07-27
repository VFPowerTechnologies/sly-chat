package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.databind.ObjectMapper

inline fun <reified T : SlyMessage> convertMessageFromSerialized(messageEntry: SenderMessageEntry): T {
    val objectMapper = ObjectMapper()
    return objectMapper.readValue(messageEntry.message, T::class.java)
}

//cheating a little here
inline fun <reified T : SlyMessage> convertMessageFromSerialized(messageEntries: List<SenderMessageEntry>): List<T> {
    val objectMapper = ObjectMapper()

    return messageEntries.map {
        objectMapper.readValue(it.message, T::class.java)
    }
}
