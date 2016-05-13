package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId

/**
 * A message and its associated contact.
 *
 * @property contact Contact's user id.
 */
data class UIMessageInfo(
    val contact: UserId,
    val messages: List<UIMessage>
)