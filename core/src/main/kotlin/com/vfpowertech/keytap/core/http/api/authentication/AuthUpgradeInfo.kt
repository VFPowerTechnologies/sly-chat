package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty

/** Sent by the server if the client is using a deprecated algorithm and should upgrade to something else. */
data class AuthUpgradeInfo(
    @param:JsonProperty("hash-params")
    @get:com.fasterxml.jackson.annotation.JsonProperty("hash-params")
    val hashParams: String
)