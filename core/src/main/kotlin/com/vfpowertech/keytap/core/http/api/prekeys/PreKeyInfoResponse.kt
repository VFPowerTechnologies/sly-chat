package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

data class PreKeyInfoResponse(
    @JsonProperty("remaining")
    val remaining: Int,
    @JsonProperty("uploadCount")
    val uploadCount: Int
)