package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

data class RemoteContactUpdate(val userId: UserId, val allowedMessageLevel: AllowedMessageLevel)