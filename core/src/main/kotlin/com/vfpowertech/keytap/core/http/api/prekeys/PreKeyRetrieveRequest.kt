package com.vfpowertech.keytap.core.http.api.prekeys

data class PreKeyRetrieveRequest(
    val authToken: String,
    val username: String
)