package com.vfpowertech.keytap.core.http.api.infoService

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.api.InvalidResponseBodyException
import com.vfpowertech.keytap.core.typeRef

class InfoServiceClient(private val httpClient: HttpClient) {

    fun getGeoLocationInfo(): GeoLocationInfo? {
        val url = "http://ipinfo.io/json"

        val resp = httpClient.get(url, listOf())

        return try {
            ObjectMapper().readValue<GeoLocationInfo>(resp.body, typeRef<GeoLocationInfo>())
        }
        catch (e: JsonProcessingException) {
            throw InvalidResponseBodyException(resp, e)
        }
    }
}