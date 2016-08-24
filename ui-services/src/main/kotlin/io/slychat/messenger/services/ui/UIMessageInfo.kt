package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

/**
 * A message and its associated contact.
 *
 * @property contact Contact's user id.
 */
data class UIMessageInfo(
    val contact: UserId?,
    val groupId: GroupId?,
    val messages: List<UIMessage>
)