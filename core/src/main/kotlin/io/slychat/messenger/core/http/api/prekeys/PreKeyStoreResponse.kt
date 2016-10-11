package io.slychat.messenger.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class PreKeyStoreResponse(
    @JsonProperty("errorMessage")
    val errorMessage: String?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}