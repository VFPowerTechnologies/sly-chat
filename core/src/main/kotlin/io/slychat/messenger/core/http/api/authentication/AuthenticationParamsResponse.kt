package io.slychat.messenger.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.hashes.HashParams

data class AuthenticationParams(
    @param:JsonProperty("csrfToken")
    val csrfToken: String,

    @param:JsonProperty("hashParams")
    val hashParams: HashParams
)

data class AuthenticationParamsResponse(
    @param:JsonProperty("errorMessage")
    val errorMessage: String?,

    @param:JsonProperty("params")
    val params: AuthenticationParams?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
