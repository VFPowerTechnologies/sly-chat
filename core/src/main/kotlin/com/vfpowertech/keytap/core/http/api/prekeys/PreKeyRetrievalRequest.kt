package com.vfpowertech.keytap.core.http.api.prekeys

data class PreKeyRetrievalRequest(
    val authToken: String,
    val username: String
)