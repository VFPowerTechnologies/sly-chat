package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise
import rx.Observable

/** Handles incoming messages. */
interface MessageProcessor {
    //FIXME
    val newMessages: Observable<ConversationMessage>

    fun processMessage(sender: UserId, wrapper: SlyMessageWrapper): Promise<Unit, Exception>

    fun init()
    fun shutdown()
}
