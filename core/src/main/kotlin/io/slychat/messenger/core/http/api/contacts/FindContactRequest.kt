package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class FindContactRequest(
    @param:JsonProperty("contact-username")
    @get:JsonProperty("contact-username")
    val username: String?,

    @param:JsonProperty("contact-phone-number")
    @get:JsonProperty("contact-phone-number")
    val phoneNumber: String?
)