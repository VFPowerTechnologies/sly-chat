package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo

data class ApiContactInfo(
    @JsonProperty("id")
    val id: UserId,
    @JsonProperty("email")
    val email: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("phone-number")
    val phoneNumber: String?,
    @JsonProperty("public-key")
    val publicKey: String
) {
    fun toCore(isPending: Boolean, allowedMessageLevel: AllowedMessageLevel): ContactInfo =
        ContactInfo(id, email, name, allowedMessageLevel, isPending, phoneNumber, publicKey)
}
