package io.slychat.messenger.core.integration.web

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.SerializedKeyVault
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.http.HttpClient
import io.slychat.messenger.core.http.HttpResponse
import io.slychat.messenger.core.http.api.offline.OfflineMessagesGetResponse
import io.slychat.messenger.core.http.api.offline.SerializedOfflineMessage
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationService
import io.slychat.messenger.core.http.get
import io.slychat.messenger.core.http.postJSON
import io.slychat.messenger.core.persistence.RemoteAddressBookEntry

data class SiteAddressBook(
    @JsonProperty("entries")
    val entries: List<RemoteAddressBookEntry>
)

data class UserPushNotificationTokenInfo(
    @JsonProperty("deviceId")
    val deviceId: Int,
    @JsonProperty("token")
    val token: String,
    @JsonProperty("audioToken")
    val audioToken: String?,
    @JsonProperty("service")
    val service: PushNotificationService
)

data class UserPushNotificationTokenList(
    @JsonProperty("tokens")
    val tokens: List<UserPushNotificationTokenInfo>
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
    val email: String,
    val passwordHash: String,
    val hashParams: HashParams,
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

data class UploadPartInfo(
    @JsonProperty("n")
    val n: Int,
    @JsonProperty("size")
    val size: Long
)

data class UploadInfo(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("fileSize")
    val fileSize: Long,
    @JsonProperty("fileMetadata")
    val fileMetadata: ByteArray,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray,
    @JsonProperty("parts")
    val parts: List<UploadPartInfo>
)

class FileInfo(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("isDeleted")
    val isDeleted: Boolean,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray,
    @JsonProperty("fileMetadata")
    val fileMetadata: ByteArray,
    @JsonProperty("fileSize")
    val fileSize: Long
)

/** Client for web api server dev functionality. */
class DevClient(private val serverBaseUrl: String, private val httpClient: HttpClient) {
    private val objectMapper = ObjectMapper()

    private fun postRequestNoResponse(request: Any?, url: String) {
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

    fun getUser(email: String): SiteUser? {
        val response = httpClient.get("$serverBaseUrl/dev/users/$email")
        throwOnFailure(response)

        return objectMapper.readValue<SiteUser>(response.body, typeRef<SiteUser>())
    }

    fun addUser(siteUser: GeneratedSiteUser) {
        val user = siteUser.user
        val request = RegisterSiteUserRequest(
            user.id,
            user.email,
            siteUser.remotePasswordHash.hexify(),
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

    fun createAuthToken(email: String, deviceId: Int = DEFAULT_DEVICE_ID): AuthToken {
        val response = httpClient.postJSON("$serverBaseUrl/dev/auth/$email/$deviceId", ByteArray(0))
        throwOnFailure(response)

        return objectMapper.readValue(response.body, SiteAuthTokenData::class.java).authToken
    }

    fun getAuthToken(email: String, deviceId: Int = DEFAULT_DEVICE_ID): AuthToken {
        return getRequest("/dev/auth/$email/$deviceId", SiteAuthTokenData::class.java).authToken
    }

    fun getPreKeys(email: String, deviceId: Int = DEFAULT_DEVICE_ID): SitePreKeyData {
        return getRequest("/dev/prekeys/one-time/$email/$deviceId", SitePreKeyData::class.java)
    }

    fun addOneTimePreKeys(email: String, preKeys: List<String>, deviceId: Int = DEFAULT_DEVICE_ID) {
        val request = mapOf(
            "oneTimePreKeys" to preKeys
        )

        postRequestNoResponse(request, "/dev/prekeys/one-time/$email/$deviceId")
    }

    fun getSignedPreKey(email: String, deviceId: Int = DEFAULT_DEVICE_ID): String? {
        return getRequest("/dev/prekeys/signed/$email/$deviceId", SiteSignedPreKeyData::class.java).signedPreKey
    }

    fun setSignedPreKey(email: String, signedPreKey: String, deviceId: Int = DEFAULT_DEVICE_ID) {
        val request = mapOf(
            "signedPreKey" to signedPreKey
        )

        postRequestNoResponse(request, "/dev/prekeys/signed/$email/$deviceId")
    }

    fun getLastResortPreKey(email: String, deviceId: Int = DEFAULT_DEVICE_ID): String? {
        return getRequest("/dev/prekeys/last-resort/$email/$deviceId", SiteLastResortPreKeyData::class.java).lastResortPreKey
    }

    fun setLastResortPreKey(email: String, lastResortPreKey: String, deviceId: Int = DEFAULT_DEVICE_ID) {
        val request = mapOf(
            "lastResortPreKey" to lastResortPreKey
        )

        postRequestNoResponse(request, "/dev/prekeys/last-resort/$email/$deviceId")
    }

    fun getAddressBook(email: String): List<RemoteAddressBookEntry> {
        return getRequest("/dev/address-book/$email", SiteAddressBook::class.java).entries
    }

    fun addAddressBookEntries(email: String, entries: List<RemoteAddressBookEntry>) {
        val request = mapOf(
            "entries" to entries
        )

        postRequestNoResponse(request, "/dev/address-book/$email")
    }

    fun registerPushNotificationToken(email: String, deviceId: Int, token: String, audioToken: String?, service: PushNotificationService): String {
        val request = mapOf(
            "deviceId" to deviceId,
            "token" to token,
            "audioToken" to audioToken,
            "service" to service
        )

        return postRequest(request, "/dev/push-notifications/register/$email", String::class.java)
    }

    fun unregisterPushNotificationToken(email: String, deviceId: Int) {
        val request = mapOf(
            "deviceId" to deviceId
        )

        postRequestNoResponse(request, "/dev/push-notifications/unregister/$email")
    }

    fun getPushNotificationTokens(email: String): List<UserPushNotificationTokenInfo> {
        return getRequest("/dev/push-notifications/$email", UserPushNotificationTokenList::class.java).tokens
    }

    fun getDevices(email: String): List<Device> {
        return getRequest("/dev/users/$email/devices", typeRef<List<Device>>())
    }

    fun getMaxDevices(): Int {
        return getRequest("/dev/config/max-devices", Int::class.java)
    }

    fun getPreKeyMaxCount(): Int {
        return getRequest("/dev/prekeys/max-count", Int::class.java)
    }

    fun addDevice(email: String, registrationId: Int, state: DeviceState): Int {
        val request = mapOf(
            "registrationId" to registrationId,
            "state" to state
        )

        return postRequest(request, "/dev/users/$email/devices", Int::class.java)
    }

    fun getAddressBookHash(email: String): String {
        return getRequest("/dev/address-book/hash/$email", String::class.java)
    }

    fun getLatestVersion(): String {
        return getRequest("/dev/client-version/latest", String::class.java)
    }

    fun addOfflineMessages(userId: UserId, deviceId: Int, offlineMessages: List<SerializedOfflineMessage>) {
        val request = mapOf(
            "offlineMessages" to offlineMessages
        )

        postRequestNoResponse(request, "/dev/messages/$userId/$deviceId")
    }

    fun getOfflineMessages(userId: UserId, deviceId: Int): OfflineMessagesGetResponse {
        return getRequest("/dev/messages/$userId/$deviceId", typeRef())
    }

    fun getQuota(userId: UserId): Quota {
        return getRequest("/dev/storage/quota/$userId", typeRef())
    }

    fun getUploadInfo(userId: UserId, uploadId: String): UploadInfo? {
        return getRequest("/dev/upload/$userId/$uploadId", typeRef())
    }

    fun markPartsAsComplete(userId: UserId, uploadId: String) {
        postRequestNoResponse(null, "/dev/upload/$userId/$uploadId")
    }

    fun getFileInfo(userId : UserId, fileId: String): FileInfo? {
        return getRequest("/dev/storage/$userId/$fileId", typeRef())
    }
}