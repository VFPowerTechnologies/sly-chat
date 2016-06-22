package io.slychat.messenger.core.relay

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.relay.base.DeviceMismatchContent

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
data class ReceivedMessage(val from: SlyAddress, val content: ByteArray, val messageId: String) : RelayClientEvent
data class ServerReceivedMessage(val to: UserId, val messageId: String) : RelayClientEvent
data class MessageSentToUser(val to: UserId, val messageId: String) : RelayClientEvent
data class UserOffline(val to: UserId, val messageId: String) : RelayClientEvent
data class DeviceMismatch(val to: UserId, val messageId: String, val info: DeviceMismatchContent) : RelayClientEvent
