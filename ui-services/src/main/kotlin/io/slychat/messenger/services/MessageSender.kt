package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise
import rx.Observable

//need to clear queue on relay disconnect
//need to refill queue on relay connect
//need to process send results
//need to somehow send messages; not sure if I should just pass the relay thing directly but I'd like not to; it's messier and packs more logic into this component
//only idea I have is to have an observable and having the messengerservice handle sending stuff when the observable fires? best thing I can think of, even if I still think it's a bit eh
//that way we don't need to track connection tags in here? or maybe set that on reconnect/disconnect

//XXX why do we refill the queue on each reconnect? I guess it's to make it easier to handle an encrypted message when disconnected,
//since we need to wait for a reply; so this needs to be structured in a way where we can reset the queue state if we're waiting for a response
//and the relay goes offline

//I almost feel like it's not worth splitting this out in a separate component... I mean we basicly need to handle all the relay-related stuff in here anyways
//so the MessengerService itself becomes almost pointless? I guess it does a bit of preprocessing and handles proxying stuff from UIMessengerService in that case
interface MessageSender {
    val messageUpdates: Observable<MessageBundle>

    //let MessengerService handle writing to log after successful queue submit?
    fun addToQueue(userId: UserId, message: SlyMessageWrapper): Promise<Unit, Exception>

    fun init()

    fun shutdown()
}