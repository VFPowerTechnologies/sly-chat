package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/**
 * @property speaker Self if null.
 */
data class GroupMessageInfo(
    val speaker: UserId?,
    val info: MessageInfo
)