package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.SerializedCryptoParams
import io.slychat.messenger.core.crypto.SerializedKeyVault
import io.slychat.messenger.core.crypto.hexify
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.HttpResponse
import io.slychat.messenger.core.http.api.contacts.RemoteContactEntry
import io.slychat.messenger.core.http.get
import io.slychat.messenger.core.http.postJSON

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

data class Device(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("registrationId")
    val registrationId: Int,
    @JsonProperty("state")
    val state: DeviceState
)

data class RegisterSiteUserRequest(
    val id: UserId,
    val username: String,
    val passwordHash: String,
    val hashParams: SerializedCryptoParams,
    val publicKey: String,
    val name: String,
    val phoneNumber: String,
    val keyVault: SerializedKeyVault
)

val DEFAULT_DEVICE_ID = 1

@JsonFormat(shape = JsonFormat.Shape.NUMBER)
enum class DeviceState {
    INACTIVE,
    PENDING,
    ACTIVE
}

/** Client for web api server dev functionality. */
class DevClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val objectMapper = ObjectMapper()

    private fun postRequestNoResponse(request: Any, url: String) {
        val body = objectMapper.writeValueAsBytes(request)

        throwOnFailure(httpClient.postJSON("$serverBaseUrl$url", body))
   }

    private fun <T> postRequest(request: Any, url: String, clazz: Class<T>): T {
        val body = objectMapper.writeValueAsBytes(request)

        val response = httpClient.postJSON("$serverBaseUrl$url", body)
        throwOnFailure(response)

        return objectMapper.readValue(response.body, clazz)
    }

    private fun <T> getRequest(url: String, typeReference: TypeReference<T>): T {
        val response = httpClient.get("$serverBaseUrl$url")
        throwOnFailure(response)

        return objectMapper.readValue(response.body, typeReference)
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

    fun addUser(siteUser: GeneratedSiteUser) {
        val user = siteUser.user
        val request = RegisterSiteUserRequest(
            user.id,
            user.username,
            siteUser.keyVault.remotePasswordHash.hexify(),
            user.hashParams,
            user.publicKey,
            user.name,
            user.phoneNumber,
            user.keyVault
        )
        val body = objectMapper.writeValueAsBytes(request)
        val response = httpClient.postJSON("$serverBaseUrl/dev/users", body)
        throwOnFailure(response)
    }

    fun clear() {
        val response = httpClient.postJSON("$serverBaseUrl/dev/clear", ByteArray(0))
        throwOnFailure(response)
    }

    fun createAuthToken(username: String, deviceId: Int = DEFAULT_DEVICE_ID): AuthToken {
        val response = httpClient.postJSON("$serverBaseUrl/dev/auth/$username/$deviceId", ByteArray(0))
        throwOnFailure(response)

        return objectMapper.readValue(response.body, SiteAuthTokenData::class.java).authToken
    }

    fun getAuthToken(username: String, deviceId: Int = DEFAULT_DEVICE_ID): AuthToken {
        return getRequest("/dev/auth/$username/$deviceId", SiteAuthTokenData::class.java).authToken
    }

    fun getPreKeys(username: String, deviceId: Int = DEFAULT_DEVICE_ID): SitePreKeyData {
        return getRequest("/dev/prekeys/one-time/$username/$deviceId", SitePreKeyData::class.java)
    }

    fun addOneTimePreKeys(username: String, preKeys: List<String>, deviceId: Int = DEFAULT_DEVICE_ID) {
        val request = mapOf(
            "oneTimePreKeys" to preKeys
        )

        postRequestNoResponse(request, "/dev/prekeys/one-time/$username/$deviceId")
    }

    fun getSignedPreKey(username: String, deviceId: Int = DEFAULT_DEVICE_ID): String? {
        return getRequest("/dev/prekeys/signed/$username/$deviceId", SiteSignedPreKeyData::class.java).signedPreKey
    }

    fun setSignedPreKey(username: String, signedPreKey: String, deviceId: Int = DEFAULT_DEVICE_ID) {
        val request = mapOf(
            "signedPreKey" to signedPreKey
        )

        postRequestNoResponse(request, "/dev/prekeys/signed/$username/$deviceId")
    }

    fun getLastResortPreKey(username: String, deviceId: Int = DEFAULT_DEVICE_ID): String? {
        return getRequest("/dev/prekeys/last-resort/$username/$deviceId", SiteLastResortPreKeyData::class.java).lastResortPreKey
    }

    fun setLastResortPreKey(username: String, lastResortPreKey: String, deviceId: Int = DEFAULT_DEVICE_ID) {
        val request = mapOf(
            "lastResortPreKey" to lastResortPreKey
        )

        postRequestNoResponse(request, "/dev/prekeys/last-resort/$username/$deviceId")
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

    fun getDevices(username: String): List<Device> {
        return getRequest("/dev/users/$username/devices", typeRef<List<Device>>())
    }

    fun getMaxDevices(): Int {
        return getRequest("/dev/config/max-devices", Int::class.java)
    }

    fun getPreKeyMaxCount(): Int {
        return getRequest("/dev/prekeys/max-count", Int::class.java)
    }

    fun addDevice(username: String, registrationId: Int, state: DeviceState): Int {
        val request = mapOf(
            "registrationId" to registrationId,
            "state" to state
        )

        return postRequest(request, "/dev/users/$username/devices", Int::class.java)
    }
}