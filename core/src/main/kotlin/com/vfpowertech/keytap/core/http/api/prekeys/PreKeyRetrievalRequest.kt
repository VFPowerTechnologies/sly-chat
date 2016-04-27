package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.UserId

/**
 * @property deviceIds If empty, fetches for all available devices.
 */
data class PreKeyRetrievalRequest(
    val authToken: String,
    val userId: UserId,
    val deviceIds: List<Int>
)