package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise
import rx.Observable

/**
 * Handles queuing of messages to send to relay.
 *
 * Not concerned with actual message contents, messages are represented as bytes to be encrypted and sent to the relay.
 */
interface MessageSender {
    //XXX maybe move this out to the MessengerService?
    //probably is that we need to send out a MessageBundle to the ui with the proper message info for messages which are text messages
    //I guess we can just do a db lookup or something? stupid but w/e
    val messageUpdates: Observable<MessageBundle>

    fun addToQueue(userId: UserId, messageId: String, message: ByteArray): Promise<Unit, Exception>

    fun init()

    fun shutdown()
}