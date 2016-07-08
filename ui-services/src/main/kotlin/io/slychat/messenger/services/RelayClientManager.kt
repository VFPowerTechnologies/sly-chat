package io.slychat.messenger.services

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.core.relay.RelayClientState
import io.slychat.messenger.core.relay.RelayMessageBundle
import rx.Observable

/**
 * Wrapper around a relay connection.
 *
 * Scoped to a user's session.
 *
 * May be online or offline.
 *
 * All exposed observables fire on the main thread.
 */
interface RelayClientManager {
    /**
     * Random value from [0, Integer.MAX) to id the current connection.
     *
     * Used to prevent messages destined for a previous connection from being sent, in the event of things like message
     * encryption being completed between a disconnect and reconnect.
     */
    val connectionTag: Int

    val isOnline: Boolean

    /** Fires updates on relay connection status updates. */
    val onlineStatus: Observable<Boolean>

    val state: RelayClientState?

    /** Received relay messages. */
    val events: Observable<RelayClientEvent>

    /** Connect to the relay. */
    fun connect(userCredentials: UserCredentials)
    fun disconnect()

    fun sendMessage(connectionTag: Int, to: UserId, messageBundle: RelayMessageBundle, messageId: String)
    fun sendMessageReceivedAck(messageId: String)
    fun sendPing()
}