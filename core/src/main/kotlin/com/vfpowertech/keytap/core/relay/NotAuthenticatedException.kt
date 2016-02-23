package com.vfpowertech.keytap.core.relay

class NotAuthenticatedException(state: RelayClientState) : IllegalStateException("Currently in state $state, not authenticated")