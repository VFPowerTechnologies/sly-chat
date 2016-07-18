package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.MessageInfo

data class MessageBundle(val userId: UserId, val messages: List<MessageInfo>)