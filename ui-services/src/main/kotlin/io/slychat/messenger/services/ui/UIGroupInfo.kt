package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.persistence.GroupId

data class UIGroupInfo(
    @JsonProperty("id") val id: GroupId,
    @JsonProperty("name") val name: String
)
