package io.slychat.messenger.core.files

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

/**
 * Indicates which user shared the file; if groupId is non-null, indicates that this user shared this from a group.
 */
data class SharedFrom(
    @JsonProperty("userId")
    val userId: UserId,
    @JsonProperty("groupId")
    val groupId: GroupId?
)