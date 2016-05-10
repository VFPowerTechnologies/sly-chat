package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.PlatformContact

data class FindLocalContactsRequest(
    @JsonProperty("contacts")
    val contacts: List<PlatformContact>
)