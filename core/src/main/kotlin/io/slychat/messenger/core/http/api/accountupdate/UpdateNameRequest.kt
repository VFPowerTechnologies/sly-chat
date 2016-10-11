package io.slychat.messenger.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateNameRequest(
    @param:JsonProperty("name")
    val name: String
)