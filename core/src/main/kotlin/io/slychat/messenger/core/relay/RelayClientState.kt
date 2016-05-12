package io.slychat.messenger.core.relay

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