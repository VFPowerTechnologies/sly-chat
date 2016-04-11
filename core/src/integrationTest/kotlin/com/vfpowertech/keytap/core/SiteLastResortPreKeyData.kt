package com.vfpowertech.keytap.core

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteLastResortPreKeyData(
    @JsonProperty("lastResortPreKey")
    val lastResortPreKey: String?
)