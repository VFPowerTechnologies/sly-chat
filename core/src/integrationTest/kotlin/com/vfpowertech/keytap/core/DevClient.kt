package com.vfpowertech.keytap.core

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper

import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse

/** Client for web api server dev functionality. */
class DevClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val objectMapper = ObjectMapper()

    private fun throwOnFailure(response: HttpResponse) {
        if (!response.isSuccess)
            throw RuntimeException("Error from server: ${response.code}")
    }

    fun getUsers(): List<SiteUser> {
        val response = httpClient.get("$serverBaseUrl/dev/users")
        throwOnFailure(response)

        return objectMapper.readValue<List<SiteUser>>(response.body, typeRef<List<SiteUser>>())
    }

    fun addUser(siteUser: SiteUser) {
        val body = objectMapper.writeValueAsBytes(siteUser)
        val response = httpClient.postJSON("$serverBaseUrl/dev/users", body)
        throwOnFailure(response)
    }

    fun clear() {
        val response = httpClient.postJSON("$serverBaseUrl/dev/clear", ByteArray(0))
        throwOnFailure(response)
    }

    fun createAuthToken(username: String): String {
        val response = httpClient.postJSON("$serverBaseUrl/dev/auth/$username", ByteArray(0))
        throwOnFailure(response)

        return objectMapper.readValue(response.body, SiteAuthTokenData::class.java).authToken
    }

    fun getAuthToken(username: String): String {
        val response = httpClient.get("$serverBaseUrl/dev/auth/$username")
        throwOnFailure(response)

        return objectMapper.readValue(response.body, SiteAuthTokenData::class.java).authToken

    }
}