package io.slychat.messenger.services

sealed class RegistrationProgress {
    /** No registration in progress. */
    class Waiting() : RegistrationProgress()

    /** Registration progress update. */
    class Update(val progressText: String) : RegistrationProgress()

    /** Registration has completed. */
    class Complete(
        val successful: Boolean,
        val errorMessage: String?,
        val validationErrors: Map<String, List<String>>?
    ) : RegistrationProgress()

    /** Unexpected error occured. */
    class Error(
        val cause: Exception
    ) : RegistrationProgress()
}