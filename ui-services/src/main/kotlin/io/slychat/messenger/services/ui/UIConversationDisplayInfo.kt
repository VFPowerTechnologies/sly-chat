package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

data class UIConversationDisplayInfo(
    val userId: UserId?,
    val groupId: GroupId?,
    val groupName: String?,
    val unreadCount: Int,
    val lastMessageData: UILastMessageData?
)

data class UILastMessageData(
    val speakerName: String?,
    val speakerId: UserId?,
    val message: String?,
    val timestamp: Long
)

