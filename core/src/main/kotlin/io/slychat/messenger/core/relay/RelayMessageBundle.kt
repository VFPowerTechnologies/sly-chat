package io.slychat.messenger.core.relay

data class RelayUserMessage(val deviceId: Int, val registrationId: Int, val message: Any)

data class RelayMessageBundle(val messages: List<RelayUserMessage>)