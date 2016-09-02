package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class FindContactResponse(
    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("contact-info")
    @get:JsonProperty("contact-info")
    val contactInfo: ApiContactInfo?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
