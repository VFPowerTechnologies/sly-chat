package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.ConversationId
import java.util.*

//https://youtrack.jetbrains.com/issue/KT-15313
//direct inheritance causes issues when compiling with gradle > 3.1
internal class MessageListMap {
    private val underlying = HashMap<ConversationId, MutableList<String>>()

    operator fun get(key: ConversationId): MutableList<String> {
        val v = underlying[key]
        if (v != null)
            return v

        val list = ArrayList<String>()
        underlying[key] = list

        return list
    }

    fun toMap(): Map<ConversationId, List<String>> {
        return HashMap(underlying)
    }

    fun isNotEmpty(): Boolean {
        return underlying.isNotEmpty()
    }
}
