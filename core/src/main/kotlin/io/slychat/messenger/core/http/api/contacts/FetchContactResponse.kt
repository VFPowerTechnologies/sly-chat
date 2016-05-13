package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.persistence.ContactInfo

data class FetchContactResponse(
    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("contact-info")
    @get:JsonProperty("contact-info")
    val contactInfo: ContactInfo?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
