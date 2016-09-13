package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

/** Handles incoming messages. */
interface MessageProcessor {
    fun processMessage(sender: UserId, wrapper: SlyMessageWrapper): Promise<Unit, Exception>

    fun init()
    fun shutdown()
}
