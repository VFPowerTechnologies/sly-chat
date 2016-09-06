package io.slychat.messenger.services

import io.slychat.messenger.services.messaging.ConversationMessage
import rx.Observable

//XXX this needs a better name
interface ConversationWatcher {
    val messageUpdates: Observable<ConversationMessage>

    fun init()
    fun shutdown()
}

