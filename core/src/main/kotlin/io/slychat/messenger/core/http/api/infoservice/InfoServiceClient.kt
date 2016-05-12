package io.slychat.messenger.core.http.api.infoservice

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.api.InvalidResponseBodyException
import io.slychat.messenger.core.typeRef

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