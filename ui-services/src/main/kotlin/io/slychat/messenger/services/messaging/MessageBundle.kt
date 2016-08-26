package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.MessageInfo

data class MessageBundle(
    val userId: UserId,
    val groupId: GroupId?,
    val messages: List<MessageInfo>
)
