package io.slychat.messenger.core.relay

class NotAuthenticatedException(state: RelayClientState) : IllegalStateException("Currently in state $state, not authenticated")