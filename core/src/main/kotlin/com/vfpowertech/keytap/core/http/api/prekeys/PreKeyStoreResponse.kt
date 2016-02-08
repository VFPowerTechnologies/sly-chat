package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

data class PreKeyStoreResponse(
    @param:JsonProperty("successful")
    @get:JsonProperty("successful")
    val successful: Boolean,

    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?
)