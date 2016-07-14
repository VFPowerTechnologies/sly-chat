package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise
import rx.Observable

/** Handles incoming messages. */
interface MessageProcessorService {
    val newMessages: Observable<MessageBundle>

    fun processMessage(userId: UserId, wrapper: SlyMessageWrapper): Promise<Unit, Exception>

    //TODO self-messages
    //maybe send a self-message via processMessages
    //for the ui side, just return the generated MessageInfo and do the db write in the bg
    //if this fails, then ???; right now we just essentially crash anyways
}
