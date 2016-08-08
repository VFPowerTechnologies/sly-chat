package io.slychat.messenger.core.relay

import io.slychat.messenger.core.UserId
import rx.Observable

/**
 * Higher-level abstraction over a relay server connection.
 *
 * Must only be used on the main app thread.
 * Once disconnected, cannot be reused, as the events observable will be closed.
 *
 * Will disconnect on failed authentication.
 */
interface RelayClient {
    val state: RelayClientState
    val events: Observable<RelayClientEvent>

    fun connect()
    fun disconnect()
    fun sendMessage(to: UserId, content: RelayMessageBundle, messageId: String)
    fun sendMessageReceivedAck(messageId: String)
    fun sendPing()
}
