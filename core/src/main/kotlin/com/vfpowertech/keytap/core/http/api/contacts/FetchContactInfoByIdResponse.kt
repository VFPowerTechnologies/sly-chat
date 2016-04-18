package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.persistence.ContactInfo

data class FetchContactInfoByIdResponse(
    @JsonProperty("contacts")
    val contacts: List<ContactInfo>
)