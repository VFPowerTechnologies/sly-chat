package io.slychat.messenger.services.ui

/** Result of selecting something from a dialog. */
data class UISelectionDialogResult<out T>(
    val ok: Boolean,
    val value: T?
)