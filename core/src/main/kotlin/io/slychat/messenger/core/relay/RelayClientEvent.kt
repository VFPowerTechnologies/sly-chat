package io.slychat.messenger.core.relay

import io.slychat.messenger.core.KeyTapAddress
import io.slychat.messenger.core.UserId

/** Higher-level translations of underlying relay messages. Emitted by RelayClient. */
interface RelayClientEvent

class ConnectionEstablished() : RelayClientEvent
/** Emitted if connection failed. */
data class ConnectionFailure(val error: Throwable) : RelayClientEvent
/**
 * @property wasRequested Indicates whether or not the client requested the disconnection.
 */
data class ConnectionLost(val wasRequested: Boolean, val error: Throwable? = null) : RelayClientEvent
class AuthenticationSuccessful() : RelayClientEvent
class AuthenticationFailure() : RelayClientEvent
class AuthenticationExpired() : RelayClientEvent
data class ReceivedMessage(val from: KeyTapAddress, val content: ByteArray, val messageId: String) : RelayClientEvent
data class ServerReceivedMessage(val to: UserId, val messageId: String) : RelayClientEvent
data class MessageSentToUser(val to: UserId, val messageId: String) : RelayClientEvent
data class UserOffline(val to: UserId, val messageId: String) : RelayClientEvent
