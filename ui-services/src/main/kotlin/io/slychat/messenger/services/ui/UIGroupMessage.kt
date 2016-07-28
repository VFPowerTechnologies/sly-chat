package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId

data class UIGroupMessage(
    val speaker: UserId?,
    val info: UIMessage
)