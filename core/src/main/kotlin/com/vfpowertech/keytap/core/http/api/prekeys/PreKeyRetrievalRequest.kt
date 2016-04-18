package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.UserId

data class PreKeyRetrievalRequest(
    val authToken: String,
    val userId: UserId
)