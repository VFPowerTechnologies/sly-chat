package io.slychat.messenger.services.messaging

import nl.komponents.kovenant.Promise
import rx.Observable

/**
 * Handles queuing of messages to send to relay.
 *
 * Not concerned with actual message contents, messages are represented as bytes to be encrypted and sent to the relay.
 */
interface MessageSender {
    val messageSent: Observable<MessageSendRecord>

    fun addToQueue(entry: SenderMessageEntry): Promise<Unit, Exception>

    fun addToQueue(messages: List<SenderMessageEntry>): Promise<Unit, Exception>

    fun init()

    fun shutdown()
}
