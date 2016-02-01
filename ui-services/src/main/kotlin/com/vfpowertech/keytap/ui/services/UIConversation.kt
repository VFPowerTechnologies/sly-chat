package com.vfpowertech.keytap.ui.services

/** Represents a conversation with a contact and its related info. */
data class UIConversation(
    val contact: UIContactInfo,
    val info: UIConversationInfo
)