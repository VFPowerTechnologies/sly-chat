package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.contacts.encryptRemoteAddressBookEntries
import io.slychat.messenger.core.http.api.registration.RegistrationInfo
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DevClientTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private val password = userManagement.defaultPassword
    private val username = userManagement.dummyRegistrationInfo.email

    @Before
    fun before() {
        devClient.clear()
    }

    @Test
    fun `user functionality should work`() {
        //users
        val userA = userManagement.newSiteUser(RegistrationInfo(username, "a", "000-000-0000"), password)
        val siteUser = userA.user

        devClient.addUser(userA)

        val users = devClient.getUsers()

        if (users != listOf(siteUser))
            throw DevServerInsaneException("Register functionality failed")

        val foundUser = assertNotNull(devClient.getUser(siteUser.username))

        assertEquals(siteUser, foundUser, "getUser returned invalid user")
    }

    @Test
    fun `address book hash functionality should work`() {
        userManagement.injectNewSiteUser()

        //address book hash
        assertEquals(emptyMd5, devClient.getAddressBookHash(username), "Unable to fetch address book hash")
    }

    @Test
    fun `address book functionality should work`() {
        val userA = userManagement.injectNewSiteUser()

        val userB = userManagement.newSiteUser(RegistrationInfo("b@a.com", "B", "000-000-0000"), password)

        val update = AddressBookUpdate.Contact(userB.user.id, AllowedMessageLevel.ALL)
        val contactsA = encryptRemoteAddressBookEntries(userA.keyVault, listOf(update))
        devClient.addAddressBookEntries(username, contactsA)

        val contacts = devClient.getAddressBook(username)

        if (contacts != contactsA)
            throw DevServerInsaneException("Address book functionality failed")
    }

    @Test
    fun `auth token functionality should work`() {
        userManagement.injectNewSiteUser()

        val authToken = devClient.createAuthToken(username)

        val gotToken = devClient.getAuthToken(username)

        if (gotToken != authToken)
            throw DevServerInsaneException("Auth token functionality failed")
    }

    @Test
    fun `prekey functionality should work`() {
        userManagement.injectNewSiteUser()

        //prekeys
        val oneTimePreKeys = listOf("a", "b").sorted()
        devClient.addOneTimePreKeys(username, oneTimePreKeys)

        val gotPreKeys = devClient.getPreKeys(username).oneTimePreKeys.sorted()
        if (gotPreKeys != oneTimePreKeys)
            throw DevServerInsaneException("One-time prekey functionality failed")

        val signedPreKey = "s"
        devClient.setSignedPreKey(username, signedPreKey)

        if (devClient.getSignedPreKey(username) != signedPreKey)
            throw DevServerInsaneException("Signed prekey functionality failed")

        val lastResortPreKey = "l"
        devClient.setLastResortPreKey(username, lastResortPreKey)

        if (devClient.getLastResortPreKey(username) != lastResortPreKey)
            throw DevServerInsaneException("Last resort prekey functionality failed")
    }

    @Test
    fun `device functionality should work`() {
        userManagement.injectNewSiteUser()

        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        val deviceId2 = devClient.addDevice(username, defaultRegistrationId + 1, DeviceState.INACTIVE)

        val devices = devClient.getDevices(username)

        val expected = listOf(
            Device(deviceId, defaultRegistrationId, DeviceState.ACTIVE),
            Device(deviceId2, defaultRegistrationId + 1, DeviceState.INACTIVE)
        )

        if (devices != expected)
            throw DevServerInsaneException("Device functionality failed")
    }

    @Test
    fun `gcm functionality should work`() {
        userManagement.injectNewSiteUser()
        val deviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        //GCM
        val gcmToken = randomUUID()
        devClient.registerGcmToken(username, deviceId, gcmToken)

        val gcmTokens = devClient.getGcmTokens(username)

        if (gcmTokens != listOf(UserGcmTokenInfo(deviceId, gcmToken)))
            throw DevServerInsaneException("GCM functionality failed")

        devClient.unregisterGcmToken(username, deviceId)

        if (devClient.getGcmTokens(username).size != 0)
            throw DevServerInsaneException("GCM functionality failed")
    }
}