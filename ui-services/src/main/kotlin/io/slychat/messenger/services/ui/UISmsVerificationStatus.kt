package io.slychat.messenger.services.ui

/**
 * @property successful Whether or not the verification was successful. When false, errorMessage is non-null.
 * @property errorMessage Only set when successful is false.
 */
data class UISmsVerificationStatus(
    val successful: Boolean,
    val errorMessage: String?
)