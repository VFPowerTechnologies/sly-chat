package com.vfpowertech.keytap.core

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteSignedPreKeyData(
    @JsonProperty("signedPreKey")
    val signedPreKey: String?
)