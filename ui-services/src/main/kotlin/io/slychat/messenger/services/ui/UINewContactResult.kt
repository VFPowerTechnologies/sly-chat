package io.slychat.messenger.services.ui

data class UINewContactResult(
    val successful: Boolean,
    val errorMessage: String?,
    val contactDetails: UIContactDetails?
)