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

data class UserGcmTokenInfo(
    @JsonProperty("installationId")
    val installationId: String,
    @JsonProperty("token")
    val token: String
)

data class UserGcmTokenList(
    @JsonProperty("tokens")
    val tokens: List<UserGcmTokenInfo>
)

/** Client for web api server dev functionality. */
class DevClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val objectMapper = ObjectMapper()

    private fun postRequestNoResponse(request: Any, url: String) {
        val body = objectMapper.writeValueAsBytes(request)

        throwOnFailure(httpClient.postJSON("$serverBaseUrl$url", body))
   }

    private fun <T> getRequest(url: String, clazz: Class<T>): T {
        val response = httpClient.get("$serverBaseUrl$url")
        throwOnFailure(response)

        return objectMapper.readValue(response.body, clazz)
    }

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
        return getRequest("/dev/auth/$username", SiteAuthTokenData::class.java).authToken
    }

    fun getPreKeys(username: String): SitePreKeyData {
        return getRequest("/dev/prekeys/one-time/$username", SitePreKeyData::class.java)
    }

    fun addOneTimePreKeys(username: String, preKeys: List<String>) {
        val request = mapOf(
            "oneTimePreKeys" to preKeys
        )

        postRequestNoResponse(request, "/dev/prekeys/one-time/$username")
    }

    fun getSignedPreKey(username: String): String? {
        return getRequest("/dev/prekeys/signed/$username", SiteSignedPreKeyData::class.java).signedPreKey
    }

    fun setSignedPreKey(username: String, signedPreKey: String) {
        val request = mapOf(
            "signedPreKey" to signedPreKey
        )

        postRequestNoResponse(request, "/dev/prekeys/signed/$username")
    }

    fun getContactList(username: String): List<RemoteContactEntry> {
        return getRequest("/dev/contact-list/$username", SiteContactList::class.java).contacts
    }

    fun addContacts(username: String, contacts: List<RemoteContactEntry>) {
        val request = mapOf(
            "contacts" to contacts
        )

        postRequestNoResponse(request, "/dev/contact-list/$username")
    }

    fun registerGcmToken(username: String, installationId: String, token: String) {
        val request = mapOf(
            "installationId" to installationId,
            "token" to token
        )

        postRequestNoResponse(request, "/dev/gcm/register/$username")
    }

    fun unregisterGcmToken(username: String, installationId: String) {
        val request = mapOf(
            "installationId" to installationId
        )

        postRequestNoResponse(request, "/dev/gcm/unregister/$username")
    }

    fun getGcmTokens(username: String): List<UserGcmTokenInfo> {
        return getRequest("/dev/gcm/$username", UserGcmTokenList::class.java).tokens
    }
}