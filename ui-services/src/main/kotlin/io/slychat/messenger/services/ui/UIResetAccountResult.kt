package io.slychat.messenger.services.ui

/**
 * @property successful Whether or not the reset request was successful. When false, errorMessage is non-null.
 * @property errorMessage Only set when successful is false.
 */
data class UIResetAccountResult(
    val successful: Boolean,
    val errorMessage: String?
)