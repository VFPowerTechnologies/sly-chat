package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/**
 * @property speaker Self if null.
 */
data class ConversationMessageInfo(
    val speaker: UserId?,
    val info: MessageInfo,
    val failures: Map<UserId, MessageSendFailure> = emptyMap()
) {
    val hasFailures: Boolean
        get() = failures.isNotEmpty()
}