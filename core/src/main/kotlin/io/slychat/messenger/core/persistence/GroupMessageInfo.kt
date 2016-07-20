package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

data class GroupMessageInfo(
    val sender: UserId,
    val info: MessageInfo
)