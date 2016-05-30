package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

enum class RemoteContactUpdateType {
    ADD,
    REMOVE
}

data class RemoteContactUpdate(val userId: UserId, val type: RemoteContactUpdateType)