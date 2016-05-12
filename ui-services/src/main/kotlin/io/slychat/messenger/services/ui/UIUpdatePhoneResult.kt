package io.slychat.messenger.services.ui

/**
 * @property successful Whether or not the update was successful. When false, errorMessage is non-null, and validationErrors may be non-null.
 * @property errorMessage Only set when successful is false.
 */
data class UIUpdatePhoneResult(
    val successful: Boolean,
    val errorMessage: String?
)