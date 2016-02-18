package com.vfpowertech.keytap.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient

/** Client for web api server dev functionality. */
class DevClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val objectMapper = ObjectMapper()

    fun getUsers(): List<SiteUser> {
        val response = httpClient.get("$serverBaseUrl/dev/users")
        if (!response.isSuccess)
            throw RuntimeException("Error from server: ${response.code}")

        return objectMapper.readValue<List<SiteUser>>(response.body, typeRef<List<SiteUser>>())
    }

    fun addUser(siteUser: SiteUser) {
        val body = objectMapper.writeValueAsBytes(siteUser)
        val response = httpClient.postJSON("$serverBaseUrl/dev/users", body)
        if (!response.isSuccess)
            throw RuntimeException("Error from server: ${response.code}")
    }

    fun clear() {
        val response = httpClient.postJSON("$serverBaseUrl/dev/clear", ByteArray(0))
        if (!response.isSuccess)
            throw RuntimeException("Error from server: ${response.code}")
    }
}