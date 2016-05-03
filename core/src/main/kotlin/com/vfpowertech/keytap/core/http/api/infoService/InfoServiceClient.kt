package com.vfpowertech.keytap.core.http.api.infoService

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.typeRef

class InfoServiceClient(private val httpClient: HttpClient) {

    fun getGeoLocationInfo(): GeoLocationInfo? {
        val url = "http://ipinfo.io/json"

        val resp = httpClient.get(url)

        val geoLocationInfo = ObjectMapper().readValue<GeoLocationInfo>(resp.body, typeRef<GeoLocationInfo>())

        return geoLocationInfo
    }

}

