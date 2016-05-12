package io.slychat.messenger.services.ui

/**
 * @property successful Whether or not the registration was successful. When false, errorMessage is non-null, and validationErrors may be non-null.
 * @property errorMessage Only set when successful is false.
 * @property validationErrors May be null even if successful is false, Map of field name to list of validation error messages.
 */
data class UIRegistrationResult(
    val successful: Boolean,
    val errorMessage: String?,
    val validationErrors: Map<String, List<String>>?
)