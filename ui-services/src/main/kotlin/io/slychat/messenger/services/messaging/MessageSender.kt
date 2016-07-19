package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.MessageMetadata
import nl.komponents.kovenant.Promise
import rx.Observable

class MessageEntry(val metadata: MessageMetadata, val message: ByteArray)

/**
 * Handles queuing of messages to send to relay.
 *
 * Not concerned with actual message contents, messages are represented as bytes to be encrypted and sent to the relay.
 */
interface MessageSender {
    val messageSent: Observable<MessageMetadata>

    fun addToQueue(metadata: MessageMetadata, message: ByteArray): Promise<Unit, Exception>

    fun addToQueue(messages: Iterable<MessageEntry>): Promise<Unit, Exception>

    fun init()

    fun shutdown()
}