package io.slychat.messenger.services.ui

/** Represents a conversation with a contact and its related info. */
data class UIConversation(
    val contact: UIContactInfo,
    val status: UIConversationInfo
)