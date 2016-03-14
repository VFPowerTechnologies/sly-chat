package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class FindLocalContactsResponse(
    @JsonProperty("contacts")
    val contacts: List<ContactInfo>
)