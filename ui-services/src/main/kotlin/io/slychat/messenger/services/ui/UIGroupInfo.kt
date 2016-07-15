package io.slychat.messenger.services.ui

import io.slychat.messenger.core.persistence.GroupId

data class UIGroupInfo(
    val id: GroupId,
    val name: String
)