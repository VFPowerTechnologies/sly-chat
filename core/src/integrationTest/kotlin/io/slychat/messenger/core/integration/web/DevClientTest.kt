package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.contacts.encryptRemoteAddressBookEntries
import io.slychat.messenger.core.http.api.offline.SerializedOfflineMessage
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationService
import io.slychat.messenger.core.http.api.registration.RegistrationInfo
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import org.assertj.core.api.Assertions.assertThat
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

        val foundUser = assertNotNull(devClient.getUser(siteUser.email))

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
    fun `push notification functionality should work`() {
        userManagement.injectNewSiteUser()

        val androidDeviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)
        val iosDeviceId = devClient.addDevice(username, defaultRegistrationId, DeviceState.ACTIVE)

        val gcmToken = randomUUID()
        val apnsToken = randomUUID()
        val audioToken = randomUUID()

        devClient.registerPushNotificationToken(username, androidDeviceId, gcmToken, null, PushNotificationService.GCM)
        devClient.registerPushNotificationToken(username, iosDeviceId, apnsToken, audioToken, PushNotificationService.APN)

        val deviceTokens = devClient.getPushNotificationTokens(username)

        val expectedInfo = listOf(
            UserPushNotificationTokenInfo(androidDeviceId, gcmToken, null, PushNotificationService.GCM),
            UserPushNotificationTokenInfo(iosDeviceId, apnsToken, audioToken, PushNotificationService.APN)
        )

        assertThat(deviceTokens).apply {
            describedAs("Should contain the added tokens")
            containsAll(expectedInfo)
        }

        devClient.unregisterPushNotificationToken(username, androidDeviceId)
        devClient.unregisterPushNotificationToken(username, iosDeviceId)

        assertThat(devClient.getPushNotificationTokens(username)).apply {
            describedAs("Should return no tokens after unregistration")
            isEmpty()
        }
    }

    @Test
    fun `offline message functionality should work`() {
        val fromUser = userManagement.injectNamedSiteUser("a@a.com")
        val fromUserId = fromUser.user.id
        val fromDeviceId = devClient.addDevice(fromUser.user.email, defaultRegistrationId, DeviceState.ACTIVE)

        val toUser = userManagement.injectNamedSiteUser("b@a.com")
        val toUserId = toUser.user.id
        val toDeviceId = devClient.addDevice(toUser.user.email, defaultRegistrationId, DeviceState.ACTIVE)

        val offlineMessages = (1..3).map {
            SerializedOfflineMessage(SlyAddress(fromUserId, fromDeviceId), it.toLong(), it.toString())
        }

        devClient.addOfflineMessages(toUserId, toDeviceId, offlineMessages)

        val receivedOfflineMessages = devClient.getOfflineMessages(toUserId, toDeviceId)

        assertThat(receivedOfflineMessages.messages).apply {
            describedAs("Should return the stored offline messages")
            containsExactlyElementsOf(offlineMessages)
        }
    }
}