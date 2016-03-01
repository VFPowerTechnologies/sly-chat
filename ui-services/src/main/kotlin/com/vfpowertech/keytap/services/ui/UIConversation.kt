package com.vfpowertech.keytap.services.ui

/** Represents a conversation with a contact and its related info. */
data class UIConversation(
    val contact: UIContactDetails,
    val status: UIConversationStatus
)