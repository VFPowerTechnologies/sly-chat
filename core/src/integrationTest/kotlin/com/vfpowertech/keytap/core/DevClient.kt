package com.vfpowertech.keytap.core

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.HttpClient
import com.vfpowertech.keytap.core.http.HttpResponse
import com.vfpowertech.keytap.core.http.api.contacts.RemoteContactEntry

data class SiteContactList(
    @JsonProperty("contacts")
    val contacts: List<RemoteContactEntry>
)

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

    fun getPreKeys(username: String): SitePreKeyData {
        val response = httpClient.get("$serverBaseUrl/dev/prekeys/one-time/$username")
        throwOnFailure(response)

        return objectMapper.readValue(response.body, SitePreKeyData::class.java)
    }

    fun addOneTimePreKeys(username: String, preKeys: List<String>) {
        val request = mapOf(
            "oneTimePreKeys" to preKeys
        )
        val body = objectMapper.writeValueAsBytes(request)
        val response = httpClient.postJSON("$serverBaseUrl/dev/prekeys/one-time/$username", body)
        throwOnFailure(response)
    }

    fun getSignedPreKey(username: String): String? {
        val response = httpClient.get("$serverBaseUrl/dev/prekeys/signed/$username")
        throwOnFailure(response)

        return objectMapper.readValue(response.body, SiteSignedPreKeyData::class.java).signedPreKey
    }

    fun setSignedPreKey(username: String, signedPreKey: String) {
        val request = mapOf(
            "signedPreKey" to signedPreKey
        )
        val body = objectMapper.writeValueAsBytes(request)
        val response = httpClient.postJSON("$serverBaseUrl/dev/prekeys/signed/$username", body)
        throwOnFailure(response)
    }

    fun getContactList(username: String): List<RemoteContactEntry> {
        val response = httpClient.get("$serverBaseUrl/dev/contact-list/$username")
        throwOnFailure(response)

        return objectMapper.readValue(response.body, SiteContactList::class.java).contacts
    }

    fun addContacts(username: String, contacts: List<RemoteContactEntry>) {
        val request = mapOf(
            "contacts" to contacts
        )

        val body = objectMapper.writeValueAsBytes(request)

        val response = httpClient.postJSON("$serverBaseUrl/dev/contact-list/$username", body)

        throwOnFailure(response)
    }
}