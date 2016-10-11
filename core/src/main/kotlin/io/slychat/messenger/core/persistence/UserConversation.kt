package io.slychat.messenger.core.persistence

data class UserConversation(
    val contact: ContactInfo,
    val info: ConversationInfo
)