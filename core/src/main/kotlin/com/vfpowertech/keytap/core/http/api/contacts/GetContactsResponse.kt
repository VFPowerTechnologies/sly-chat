package com.vfpowertech.keytap.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class GetContactsResponse(@JsonProperty("contacts") val contacts: List<RemoteContactEntry>)