package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.require

/** A API result union. May either contain an error or a value, but not both. */
data class ApiResult<out T>(
    @JsonProperty("error")
    val error: ApiError?,

    @JsonProperty("value")
    val value: T?
) {
    init {
        require(error != null || value != null, "Empty ApiResult")
        require(error == null || value == null, "ApiResult must not contain both an error and a value")
    }

    val isError: Boolean = error != null
}
