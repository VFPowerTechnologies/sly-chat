package com.vfpowertech.keytap.core.http.api.infoService

import com.fasterxml.jackson.annotation.JsonProperty

data class GeoLocationInfo(
    @param:JsonProperty("ip")
    @get:JsonProperty("ip")
    val ip: String?,

    @param:JsonProperty("hostname")
    @get:JsonProperty("hostname")
    val hostname: String?,

    @param:JsonProperty("city")
    @get:JsonProperty("city")
    val city: String?,

    @param:JsonProperty("loc")
    @get:JsonProperty("loc")
    val loc: String?,

    @param:JsonProperty("org")
    @get:JsonProperty("org")
    val org: String?,

    @param:JsonProperty("postal")
    @get:JsonProperty("postal")
    val postal: String?,

    @param:JsonProperty("country")
    @get:JsonProperty("country")
    val country: String?,

    @param:JsonProperty("region")
    @get:JsonProperty("region")
    val region: String?
)