package com.vfpowertech.keytap.core.relay

enum class RelayClientState {
    DISCONNECTING,
    DISCONNECTED,
    CONNECTING,
    /** Connected but not yet authenticated. */
    CONNECTED,
    /** Authentication request pending. */
    AUTHENTICATING,
    AUTHENTICATED
}