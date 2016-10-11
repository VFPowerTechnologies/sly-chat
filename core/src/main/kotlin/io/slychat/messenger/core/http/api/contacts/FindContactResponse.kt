package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class FindContactResponse(
    @JsonProperty("errorMessage")
    val errorMessage: String?,

    @JsonProperty("contactInfo")
    val contactInfo: ApiContactInfo?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
