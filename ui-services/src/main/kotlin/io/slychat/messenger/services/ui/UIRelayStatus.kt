package io.slychat.messenger.services.ui

/**
 * @property online Whether or not a connection the relay server is established.
 * @property lastError If online is false, then this is an informational error message indicating a possible cause.
 * @property connectedTo Hostname of connected relay server. (TODO)
 */
data class UIRelayStatus(
    val online: Boolean,
    val lastError: String?,
    val connectedTo: String?
)