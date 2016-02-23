package com.vfpowertech.keytap.core.relay

/** Higher-level translations of underlying relay messages. Emitted by RelayClient. */
interface RelayClientEvent

class ConnectionEstablished() : RelayClientEvent
/**
 * @property wasRequested Indicates whether or not the client requested the disconnection.
 */
data class ConnectionLost(val wasRequested: Boolean) : RelayClientEvent
class AuthenticationSuccessful() : RelayClientEvent
class AuthenticationFailure() : RelayClientEvent
class AuthenticationExpired() : RelayClientEvent
data class ReceivedMessage(val from: String, val message: String, val messageId: String) : RelayClientEvent
data class ServerReceivedMessage(val to: String, val messageId: String) : RelayClientEvent
data class MessageSentToUser(val to: String, val messageId: String) : RelayClientEvent
data class UserOffline(val to: String, val messageId: String) : RelayClientEvent
