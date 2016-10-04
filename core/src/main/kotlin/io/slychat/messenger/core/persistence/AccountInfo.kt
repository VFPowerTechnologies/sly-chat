package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId

data class AccountInfo(
    @JsonProperty("id")
    val id: UserId,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("email")
    val email: String,

    @JsonProperty("phoneNumber")
    val phoneNumber: String,

    @JsonProperty("deviceId")
    val deviceId: Int
)
