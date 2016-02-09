package com.vfpowertech.keytap.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.require

/** A API result union. May either contain an error or a value, but not both. */
data class ApiResult<T>(
    @param:JsonProperty("error")
    @get:JsonProperty("error")
    val error: ApiError?,

    @param:JsonProperty("value")
    @get:JsonProperty("value")
    val value: T?
) {
    init {
        require(error != null || value != null, "Empty ApiResult")
        require(error == null || value == null, "ApiResult must not contain both an error and a value")
    }

    val isError: Boolean = error != null
}
