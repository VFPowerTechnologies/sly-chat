package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.signal.GeneratedPreKeys
import io.slychat.messenger.core.crypto.signal.generateLastResortPreKey
import io.slychat.messenger.core.crypto.signal.generatePrekeys
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.ApiException
import io.slychat.messenger.core.http.api.prekeys.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import kotlin.test.*

class WebApiPreKeysTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val password = userManagement.defaultPassword
    private val invalidUserCredentials = UserCredentials(SlyAddress(UserId(999999), 999), AuthToken(""))

    @Before
    fun before() {
        devClient.clear()
    }

    fun generatePreKeysForRequest(keyVault: KeyVault): Pair<GeneratedPreKeys, PreKeyRecord> {
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        val lastResortPreKey = generateLastResortPreKey()

        return generatedPreKeys to lastResortPreKey
    }

    @Test
    fun `prekey info should reflect the current server prekey count`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.email

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(siteUser.user.email, deviceId)

        val maxCount = devClient.getPreKeyMaxCount()

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId, maxCount)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val response = client.getInfo(siteUser.getUserCredentials(authToken))

        assertEquals(generatedPreKeys.oneTimePreKeys.size, response.remaining, "Invalid remaining keys")
        assertEquals(0, response.uploadCount, "Invalid uploadCount")
    }

    @Test
    fun `prekey storage request should fail when an invalid auth token is used`() {
        val keyVault = generateNewKeyVault(password)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        assertFailsWith(UnauthorizedException::class) {
            client.store(invalidUserCredentials, request)
        }
    }

    fun injectPreKeys(username: String, keyVault: KeyVault, deviceId: Int = DEFAULT_DEVICE_ID, count: Int = 1): GeneratedPreKeys {
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, count)
        devClient.addOneTimePreKeys(username, serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys), deviceId)
        devClient.setSignedPreKey(username, serializeSignedPreKey(generatedPreKeys.signedPreKey), deviceId)
        return generatedPreKeys
    }

    fun injectLastResortPreKey(username: String, deviceId: Int = DEFAULT_DEVICE_ID): PreKeyRecord {
        val lastResortPreKey = generateLastResortPreKey()
        devClient.setLastResortPreKey(username, serializeOneTimePreKeys(listOf(lastResortPreKey))[0], deviceId)
        return lastResortPreKey
    }

    fun assertPreKeysStored(username: String, deviceId: Int, generatedPreKeys: GeneratedPreKeys, lastResortPreKey: PreKeyRecord) {
        val preKeys = devClient.getPreKeys(username, deviceId)

        val expectedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)
        val expectedLastResortPreKey = serializePreKey(lastResortPreKey)

        assertEquals(expectedOneTimePreKeys, preKeys.oneTimePreKeys, "One-time prekeys don't match")
        assertEquals(expectedSignedPreKey, preKeys.signedPreKey, "Signed prekey doesn't match")
        assertEquals(expectedLastResortPreKey, preKeys.lastResortPreKey, "Last resort prekey doesn't match")
    }

    @Test
    fun `prekey storage request should store keys on the server when a valid auth token and device id is used`() {
        val siteUser = userManagement.injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.email

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(username, deviceId)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        assertTrue(response.isSuccess)

        assertPreKeysStored(username, deviceId, generatedPreKeys, lastResortPreKey)
    }

    @Test
    fun `attempting to push prekeys to a non-registered device should fail`() {
        val siteUser = userManagement.injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.email

        val authToken = devClient.createAuthToken(username)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken), request)
        assertFalse(response.isSuccess, "Upload succeeded")
    }

    @Test
    fun `attempting to push prekeys to an inactive device should fail`() {
        val siteUser = userManagement.injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.email

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.INACTIVE)

        val authToken = devClient.createAuthToken(username, deviceId)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        assertFalse(response.isSuccess, "Upload succeeded")
    }

    @Test
    fun `attempting to push prekeys to an pending device should success and update device state`() {
        val siteUser = userManagement.injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.email
        val registrationId = defaultRegistrationId

        val deviceId = devClient.addDevice(username, registrationId, DeviceState.PENDING)

        val authToken = devClient.createAuthToken(username, deviceId)
        val (generatedPreKeys, lastResortPreKey) = generatePreKeysForRequest(keyVault)

        val request = preKeyStorageRequestFromGeneratedPreKeys(registrationId, keyVault, generatedPreKeys, lastResortPreKey)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val response = client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        assertTrue(response.isSuccess, "Upload failed: ${response.errorMessage}")

        val devices = devClient.getDevices(username)
        val updatedDevice = Device(deviceId, registrationId, DeviceState.ACTIVE)
        assertEquals(listOf(updatedDevice), devices)
    }

    @Test
    fun `prekey storage should fail when too many keys are uploaded`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.email

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(siteUser.user.email, deviceId)

        val maxCount = devClient.getPreKeyMaxCount()

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId, maxCount+1)
        val lastResortPreKey = generateLastResortPreKey()

        val request = preKeyStorageRequestFromGeneratedPreKeys(defaultRegistrationId, siteUser.keyVault, generatedPreKeys, lastResortPreKey)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val exception = assertFailsWith<ApiException> {
            client.store(siteUser.getUserCredentials(authToken, deviceId), request)
        }

        assertTrue("too many" in exception.message!!.toLowerCase(), "Invalid error message: ${exception.message}")
    }

    @Test
    fun `prekey retrieval should fail when an invalid auth token is used`() {
        val siteUser = userManagement.injectNewSiteUser()

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())
        assertFailsWith(UnauthorizedException::class) {
            client.retrieve(invalidUserCredentials, PreKeyRetrievalRequest(siteUser.user.id, listOf()))
        }
    }

    @Test
    fun `prekey retrieval should return data only for asked devices`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.email

        val requestingSiteUser = userManagement.injectNamedSiteUser("b@a.com")
        val requestingUsername = requestingSiteUser.user.email

        val deviceIds = (0..2).map { devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE) }

        val authToken = devClient.createAuthToken(requestingUsername)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val requestedDeviceIds = deviceIds.subList(0, deviceIds.size-2)
        val request = PreKeyRetrievalRequest(siteUser.user.id, requestedDeviceIds)
        val response = client.retrieve(requestingSiteUser.getUserCredentials(authToken), request)

        assertTrue(response.isSuccess)

        assertEquals(requestedDeviceIds, response.bundles.keys.toList().sorted(), "Received invalid devices")
    }

    //TODO more elaborate tests

    @Test
    fun `prekey retrieval should fail when the target user has no active devices`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.email

        val requestingSiteUser = userManagement.injectNamedSiteUser("b@a.com")
        val requestingUsername = requestingSiteUser.user.email

        devClient.addDevice(username, defaultRegistrationId, DeviceState.PENDING)
        devClient.addDevice(username, defaultRegistrationId, DeviceState.INACTIVE)

        val authToken = devClient.createAuthToken(requestingUsername)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val response = client.retrieve(requestingSiteUser.getUserCredentials(authToken), PreKeyRetrievalRequest(siteUser.user.id, listOf()))

        assertTrue(response.isSuccess)

        assertTrue(response.bundles.isEmpty(), "Bundle not empty")
    }

    @Test
    fun `prekey retrieval should return the next available prekey when a valid auth token is used`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.email

        val requestingSiteUser = userManagement.injectNamedSiteUser("b@a.com")
        val requestingUsername = requestingSiteUser.user.email

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId)

        val authToken = devClient.createAuthToken(requestingUsername)

        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val response = client.retrieve(requestingSiteUser.getUserCredentials(authToken, deviceId), PreKeyRetrievalRequest(siteUser.user.id, listOf()))

        assertTrue(response.isSuccess)

        assertNotNull(response.bundles, "No prekeys found")
        val bundles = response.bundles

        assertEquals(1, bundles.size, "Invalid number of bundles")
        assertTrue(deviceId in bundles, "Missing device id in bundle")

        val preKeyData = bundles[deviceId]!!

        val serializedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)

        assertEquals(expectedSignedPreKey, preKeyData.signedPreKey, "Signed prekey doesn't match")

        assertTrue(serializedOneTimePreKeys.contains(preKeyData.preKey), "No matching one-time prekey found")
    }

    fun assertNextPreKeyIs(userId: UserId, authToken: AuthToken, expected: PreKeyRecord, signedPreKey: SignedPreKeyRecord) {
        val client = HttpPreKeyClient(serverBaseUrl, JavaHttpClient())

        val userCredentials = UserCredentials(SlyAddress(userId, DEFAULT_DEVICE_ID), authToken)

        val response = client.retrieve(userCredentials, PreKeyRetrievalRequest(userId, listOf()))

        assertTrue(response.isSuccess)

        assertNotNull(response.bundles, "No prekeys found")
        val bundles = response.bundles

        assertEquals(1, bundles.size, "Invalid number of bundles")
        assertTrue(DEFAULT_DEVICE_ID in bundles, "Missing device id in bundle")

        val preKeyData = bundles[DEFAULT_DEVICE_ID]!!

        val expectedOneTimePreKeys = serializePreKey(expected)
        val expectedSignedPreKey = serializeSignedPreKey(signedPreKey)

        assertEquals(expectedSignedPreKey, preKeyData.signedPreKey, "Signed prekey doesn't match")

        assertEquals(expectedOneTimePreKeys, preKeyData.preKey, "One-time prekey doesn't match")
    }

    @Test
    fun `prekey retrieval should return the last resort key once no other keys are available`() {
        val siteUser = userManagement.injectNewSiteUser()
        val username = siteUser.user.email
        val userId = siteUser.user.id

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val authToken = devClient.createAuthToken(siteUser.user.email, deviceId)

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId)
        val lastResortPreKey = injectLastResortPreKey(username, deviceId)

        assertNextPreKeyIs(userId, authToken, generatedPreKeys.oneTimePreKeys[0], generatedPreKeys.signedPreKey)
        assertNextPreKeyIs(userId, authToken, lastResortPreKey, generatedPreKeys.signedPreKey)
    }
}