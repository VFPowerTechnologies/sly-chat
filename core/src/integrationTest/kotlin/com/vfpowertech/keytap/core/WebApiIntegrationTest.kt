package com.vfpowertech.keytap.core

import com.vfpowertech.keytap.core.crypto.*
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.UnauthorizedException
import com.vfpowertech.keytap.core.http.api.accountUpdate.*
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.http.api.contacts.*
import com.vfpowertech.keytap.core.http.api.prekeys.*
import com.vfpowertech.keytap.core.http.api.registration.RegistrationClient
import com.vfpowertech.keytap.core.http.api.registration.RegistrationInfo
import com.vfpowertech.keytap.core.http.api.registration.registrationRequestFromKeyVault
import com.vfpowertech.keytap.core.persistence.ContactInfo
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import kotlin.test.*

data class GeneratedSiteUser(
    val user: SiteUser,
    val keyVault: KeyVault
)

fun SiteUser.toContactInfo(): ContactInfo =
    ContactInfo(id, username, name, phoneNumber, publicKey)

class WebApiIntegrationTest {
    companion object {
        val serverBaseUrl = "http://localhost:8000"

        val defaultRegistrationId = 12345

        //kinda hacky...
        var currentUserId = 1L

        fun nextUserId(): UserId {
            val r = currentUserId
            currentUserId += 1
            return UserId(r)
        }

        fun newSiteUser(registrationInfo: RegistrationInfo, password: String): GeneratedSiteUser {
            val keyVault = generateNewKeyVault(password)
            val serializedKeyVault = keyVault.serialize()

            val user = SiteUser(
                nextUserId(),
                registrationInfo.email,
                keyVault.remotePasswordHash.hexify(),
                keyVault.remotePasswordHashParams.serialize(),
                keyVault.fingerprint,
                registrationInfo.name,
                registrationInfo.phoneNumber,
                serializedKeyVault
            )

            return GeneratedSiteUser(user, keyVault)
        }

        /** Short test for server dev functionality sanity. */
        private fun checkDevServerSanity() {
            val devClient = DevClient(serverBaseUrl, JavaHttpClient())
            val password = "test"
            val username = "a@a.com"

            devClient.clear()

            //users
            val userA = newSiteUser(RegistrationInfo(username, "a", "000-000-0000"), password)
            val siteUser = userA.user

            devClient.addUser(siteUser)

            val users = devClient.getUsers()

            if (users != listOf(siteUser))
                throw DevServerInsaneException("Register functionality failed")

            //auth token
            val authToken = devClient.createAuthToken(username)

            val gotToken = devClient.getAuthToken(username)

            if (gotToken != authToken)
                throw DevServerInsaneException("Auth token functionality failed")

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

            //contacts list
            val userB = newSiteUser(RegistrationInfo("b@a.com", "B", "000-000-0000"), password)

            val contactsA = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.id))
            devClient.addContacts(username, contactsA)

            val contacts = devClient.getContactList(username)

            assertEquals(contactsA, contacts)

            //GCM
            val installationId = randomUUID()
            val gcmToken = randomUUID()
            devClient.registerGcmToken(username, installationId, gcmToken)

            val gcmTokens = devClient.getGcmTokens(username)

            assertEquals(listOf(UserGcmTokenInfo(installationId, gcmToken)), gcmTokens)

            devClient.unregisterGcmToken(username, installationId)

            assertEquals(0, devClient.getGcmTokens(username).size)

            //devices
            val deviceId = devClient.addDevice(username, defaultRegistrationId, true)

            val devices = devClient.getDevices(username)

            assertEquals(listOf(Device(deviceId, defaultRegistrationId, true)), devices)
        }

        //only run if server is up
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            try {
                val response = JavaHttpClient().get("$serverBaseUrl/dev")
                if (response.code == 404)
                    throw ServerDevModeDisabledException()
            }
            catch (e: java.net.ConnectException) {
                Assume.assumeTrue(false)
            }

            checkDevServerSanity()
        }
    }

    val dummyRegistrationInfo = RegistrationInfo("c@a.com", "name", "000-000-0000")
    val password = "test"

    val devClient = DevClient(serverBaseUrl, JavaHttpClient())

    var counter = 1111111111;

    fun injectSiteUser(registrationInfo: RegistrationInfo): GeneratedSiteUser {
        val siteUser = newSiteUser(registrationInfo, password)

        devClient.addUser(siteUser.user)

        return siteUser
    }

    fun injectNamedSiteUser(username: String, phoneNumber: String = counter.toString()): GeneratedSiteUser {
        val registrationInfo = RegistrationInfo(username, "name", phoneNumber)
        counter++
        return injectSiteUser(registrationInfo)
    }

    fun injectNewSiteUser(): GeneratedSiteUser {
        return injectSiteUser(dummyRegistrationInfo)
    }

    @Before
    fun before() {
        counter = 1111111111
        devClient.clear()
    }

    @Test
    fun `register request should succeed when given a unique username`() {
        val keyVault = generateNewKeyVault(password)
        val request = registrationRequestFromKeyVault(dummyRegistrationInfo, keyVault)

        val client = RegistrationClient(serverBaseUrl, JavaHttpClient())
        val result = client.register(request)
        assertNull(result.errorMessage)
        assertNull(result.validationErrors)

        val users = devClient.getUsers()

        assertEquals(1, users.size)
        val user = users[0]

        val expected = SiteUser(
            //don't care about the id
            user.id,
            dummyRegistrationInfo.email,
            keyVault.remotePasswordHash.hexify(),
            keyVault.remotePasswordHashParams.serialize(),
            keyVault.fingerprint,
            dummyRegistrationInfo.name,
            dummyRegistrationInfo.phoneNumber,
            keyVault.serialize()
        )

        assertEquals(expected, user)
    }

    @Test
    fun `register request should fail when a duplicate username is used`() {
        val siteUser = injectNewSiteUser().user
        val registrationInfo = RegistrationInfo(siteUser.username, "name", "0")

        val keyVault = generateNewKeyVault(password)
        val request = registrationRequestFromKeyVault(registrationInfo, keyVault)

        val client = RegistrationClient(serverBaseUrl, JavaHttpClient())
        val result = client.register(request)

        assertNotNull(result.errorMessage, "Null error message")
        val errorMessage = result.errorMessage!!
        assertTrue(errorMessage.contains("taken"), "Invalid error message: $errorMessage}")
    }

    @Test
    fun `authentication request should succeed when given a valid username and password hash`() {
        val siteUser = injectNewSiteUser().user
        val username = siteUser.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, true)

        val client = AuthenticationClient(serverBaseUrl, JavaHttpClient())

        val paramsApiResult = client.getParams(username)

        assertNotNull(paramsApiResult.params, "getParams: params is null")

        val params = paramsApiResult.params!!

        val csrfToken = params.csrfToken
        val hashParams = HashDeserializers.deserialize(params.hashParams)

        val hash = hashPasswordWithParams(password, hashParams).hexify()

        val authRequest = AuthenticationRequest(username, hash, csrfToken, defaultRegistrationId, deviceId)

        val authApiResult = client.auth(authRequest)
        assertTrue(authApiResult.isSuccess, "auth failed: ${authApiResult.errorMessage}")

        val receivedSerializedKeyVault = authApiResult.data!!.keyVault

        assertEquals(siteUser.keyVault, receivedSerializedKeyVault)
    }

    @Test
    fun `prekey storage request should fail when an invalid auth token is used`() {
        val keyVault = generateNewKeyVault(password)
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        val lastResortPreKey = generateLastResortPreKey()

        val request = preKeyStorageRequestFromGeneratedPreKeys("a", keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyStorageClient(serverBaseUrl, JavaHttpClient())

        assertFailsWith(UnauthorizedException::class) {
            client.store(request)
        }
    }

    fun injectPreKeys(username: String, keyVault: KeyVault, deviceId: Int = DEFAULT_DEVICE_ID): GeneratedPreKeys {
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        devClient.addOneTimePreKeys(username, serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys), deviceId)
        devClient.setSignedPreKey(username, serializeSignedPreKey(generatedPreKeys.signedPreKey), deviceId)
        return generatedPreKeys
    }

    fun injectLastResortPreKey(username: String, deviceId: Int = DEFAULT_DEVICE_ID): PreKeyRecord {
        val lastResortPreKey = generateLastResortPreKey()
        devClient.setLastResortPreKey(username, serializeOneTimePreKeys(listOf(lastResortPreKey))[0], deviceId)
        return lastResortPreKey
    }

    @Test
    fun `prekey storage request should store keys on the server when a valid auth token is used`() {
        val siteUser = injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.username

        val authToken = devClient.createAuthToken(username)
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        val lastResortPreKey = generateLastResortPreKey()

        val request = preKeyStorageRequestFromGeneratedPreKeys(authToken, keyVault, generatedPreKeys, lastResortPreKey)

        val client = PreKeyStorageClient(serverBaseUrl, JavaHttpClient())

        val response = client.store(request)
        assertTrue(response.isSuccess)

        val preKeys = devClient.getPreKeys(username)

        val expectedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)
        val expectedLastResortPreKey = serializePreKey(lastResortPreKey)

        assertEquals(expectedOneTimePreKeys, preKeys.oneTimePreKeys, "One-time prekeys don't match")
        assertEquals(expectedSignedPreKey, preKeys.signedPreKey, "Signed prekey doesn't match")
        assertEquals(expectedLastResortPreKey, preKeys.lastResortPreKey, "Last resort prekey doesn't match")
    }

    @Test
    fun `prekey retrieval should fail when an invalid auth token is used`() {
        val siteUser = injectNewSiteUser()

        val client = PreKeyRetrievalClient(serverBaseUrl, JavaHttpClient())
        assertFailsWith(UnauthorizedException::class) {
            client.retrieve(PreKeyRetrievalRequest("a", siteUser.user.id, listOf()))
        }
    }

    //TODO more elaborate tests

    @Test
    fun `prekey retrieval should return the next available prekey when a valid auth token is used`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username

        val requestingSiteUser = injectNamedSiteUser("b@a.com")
        val requestingUsername = requestingSiteUser.user.username

        val deviceId = devClient.addDevice(username, defaultRegistrationId, true)

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId)

        val authToken = devClient.createAuthToken(requestingUsername)

        val client = PreKeyRetrievalClient(serverBaseUrl, JavaHttpClient())

        val response = client.retrieve(PreKeyRetrievalRequest(authToken, siteUser.user.id, listOf()))

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

    fun assertNextPreKeyIs(userId: UserId, authToken: String, expected: PreKeyRecord, signedPreKey: SignedPreKeyRecord) {
        val client = PreKeyRetrievalClient(serverBaseUrl, JavaHttpClient())

        val response = client.retrieve(PreKeyRetrievalRequest(authToken, userId, listOf()))

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
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username
        val userId = siteUser.user.id

        val deviceId = devClient.addDevice(username, defaultRegistrationId, true)

        val authToken = devClient.createAuthToken(siteUser.user.username, deviceId)

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault, deviceId)
        val lastResortPreKey = injectLastResortPreKey(username, deviceId)

        assertNextPreKeyIs(userId, authToken, generatedPreKeys.oneTimePreKeys[0], generatedPreKeys.signedPreKey)
        assertNextPreKeyIs(userId, authToken, lastResortPreKey, generatedPreKeys.signedPreKey)
    }

    @Test
    fun `new contact fetch from email should return the contact information`() {
        val siteUser = injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.id, siteUser.user.username, siteUser.user.name, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactClient(serverBaseUrl, JavaHttpClient())

        val contactResponseEmail = client.fetchContactInfo(NewContactRequest(authToken, siteUser.user.username, null))
        assertTrue(contactResponseEmail.isSuccess)

        val receivedEmailContactInfo = contactResponseEmail.contactInfo!!

        assertEquals(contactDetails, receivedEmailContactInfo)
    }

    @Test
    fun `new contact fetch from phone should return the contact information`() {
        val siteUser = injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.id, siteUser.user.username, siteUser.user.name, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactClient(serverBaseUrl, JavaHttpClient())

        val contactResponse = client.fetchContactInfo(NewContactRequest(authToken, null, siteUser.user.phoneNumber))
        assertTrue(contactResponse.isSuccess)

        val receivedContactInfo = contactResponse.contactInfo!!

        assertEquals(contactDetails, receivedContactInfo)
    }

    fun assertContactListEquals(expected: List<RemoteContactEntry>, actual: List<RemoteContactEntry>) {
        val sortedLocalContacts = expected.sortedBy { it.hash }
        val sortedRemoteContacts = actual.sortedBy { it.hash }

        assertEquals(sortedLocalContacts, sortedRemoteContacts)
    }

    @Test
    fun `Adding contacts to the user's contact list should register the contacts remotely`() {
        val siteUser = injectNewSiteUser()
        val contactUser = injectNamedSiteUser("a@a.com")

        val username = siteUser.user.username
        val authToken = devClient.createAuthToken(username)

        val encryptedContacts = encryptRemoteContactEntries(siteUser.keyVault, listOf(contactUser.user.id))
        val request = AddContactsRequest(authToken, encryptedContacts)

        val client = ContactListClient(serverBaseUrl, JavaHttpClient())
        client.addContacts(request)

        val contacts = devClient.getContactList(username)

        assertContactListEquals(encryptedContacts, contacts)
    }

    @Test
    fun `Adding a duplicate contact should cause no errors`() {
        val userA = injectNamedSiteUser("a@a.com")
        val userB = injectNamedSiteUser("b@a.com")

        val authToken = devClient.createAuthToken(userA.user.username)
        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.id))
        val request = AddContactsRequest(authToken, aContacts)

        val client = ContactListClient(serverBaseUrl, JavaHttpClient())
        client.addContacts(request)
        client.addContacts(request)

        val contacts = devClient.getContactList(userA.user.username)

        assertContactListEquals(aContacts, contacts)
    }

    @Test
    fun `Fetching a contact list should fetch only contacts for that user`() {
        val userA = injectNamedSiteUser("a@a.com")
        val userB = injectNamedSiteUser("b@a.com")
        val userC = injectNamedSiteUser("c@a.com")

        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.id))
        val bContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userC.user.id))

        devClient.addContacts(userA.user.username, aContacts)
        devClient.addContacts(userB.user.username, bContacts)

        val client = ContactListClient(serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.username)
        val response = client.getContacts(GetContactsRequest(authToken))

        assertContactListEquals(aContacts, response.contacts)
    }

    @Test
    fun `Removing a contact should remove only that contact`() {
        val userA = injectNamedSiteUser("a@a.com")
        val userB = injectNamedSiteUser("b@a.com")
        val userC = injectNamedSiteUser("c@a.com")

        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userC.user.id, userB.user.id))

        devClient.addContacts(userA.user.username, aContacts)

        val client = ContactListClient(serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.username)

        val request = RemoveContactsRequest(authToken, listOf(aContacts[0].hash))
        client.removeContacts(request)

        val contacts = devClient.getContactList(userA.user.username)

        assertContactListEquals(listOf(aContacts[1]), contacts)
    }

    @Test
    fun `findLocalContacts should find matches for both phone number and emails`() {
        val bPhoneNumber = "15555555555"
        val userA = injectNamedSiteUser("a@a.com").user
        val userB = injectNamedSiteUser("b@a.com", bPhoneNumber).user
        val userC = injectNamedSiteUser("c@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactClient(serverBaseUrl, JavaHttpClient())

        val platformContacts = listOf(
            PlatformContact("B", listOf(userC.username), listOf()),
            PlatformContact("C", listOf(), listOf(bPhoneNumber))
        )
        val request = FindLocalContactsRequest(authToken, platformContacts)
        val response = client.findLocalContacts(request)

        val expected = listOf(
            userB.toContactInfo(),
            userC.toContactInfo()
        )

        assertEquals(expected, response.contacts.sortedBy { it.email })
    }

    @Test
    fun `fetchContactInfoById should fetch users with the given ids`() {
        val userA = injectNamedSiteUser("a@a.com").user
        val userB = injectNamedSiteUser("b@a.com").user
        val userC = injectNamedSiteUser("c@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = ContactClient(serverBaseUrl, JavaHttpClient())

        val request = FetchContactInfoByIdRequest(authToken, listOf(userB.id, userC.id))
        val response = client.fetchContactInfoById(request)

        val expected = listOf(
            userB.toContactInfo(),
            userC.toContactInfo()
        )

        assertEquals(expected, response.contacts.sortedBy { it.email })
    }

    @Test
    fun `Update Phone should succeed when right password is provided`() {
        val user = injectNamedSiteUser("a@a.com")

        val client = RegistrationClient(serverBaseUrl, JavaHttpClient())

        val newPhone = "123453456"

        val request = UpdatePhoneRequest(user.user.username, user.user.passwordHash, newPhone)
        val response = client.updatePhone(request)

        assertTrue(response.isSuccess)

        val expected = SiteUser(
                user.user.id,
                user.user.username,
                user.keyVault.remotePasswordHash.hexify(),
                user.keyVault.remotePasswordHashParams.serialize(),
                user.keyVault.fingerprint,
                user.user.name,
                newPhone,
                user.keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers());
    }

    @Test
    fun `Update Phone should fail when wrong password is provided`() {
        val user = injectNamedSiteUser("a@a.com")

        val client = RegistrationClient(serverBaseUrl, JavaHttpClient())

        val request = UpdatePhoneRequest(user.user.username, "wrongPassword", "1111111111")
        val response = client.updatePhone(request)

        assertFalse(response.isSuccess)

        val expected = SiteUser(
                user.user.id,
                user.user.username,
                user.keyVault.remotePasswordHash.hexify(),
                user.keyVault.remotePasswordHashParams.serialize(),
                user.keyVault.fingerprint,
                user.user.name,
                user.user.phoneNumber,
                user.keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers());
    }

    @Test
    fun `Update Email should succeed when email is available`() {
        val userA = injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, JavaHttpClient())

        val newEmail = "b@b.com"

        val request = UpdateEmailRequest(authToken, newEmail)
        val response = client.updateEmail(request);

        assertTrue(response.isSuccess);
        assertEquals(response.accountInfo!!.username, newEmail)
    }

    @Test
    fun `Update Email should fail when email is not available`() {
        val userA = injectNamedSiteUser("a@a.com").user
        injectNamedSiteUser("b@b.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, JavaHttpClient())

        val newEmail = "b@b.com"

        val request = UpdateEmailRequest(authToken, newEmail)
        val response = client.updateEmail(request);

        assertFalse(response.isSuccess);
    }

    @Test
    fun `Update Name should succeed`() {
        val userA = injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, JavaHttpClient())

        val newName = "newName"

        val request = UpdateNameRequest(authToken, newName)
        val response = client.updateName(request);

        assertTrue(response.isSuccess);
        assertEquals(response.accountInfo!!.name, newName)
    }

    @Test
    fun `Update Phone should succeed when phone is available`() {
        val userA = injectNamedSiteUser("a@a.com").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, JavaHttpClient())

        val newPhone = "12345678901"

        val request = RequestPhoneUpdateRequest(authToken, newPhone)
        val response = client.requestPhoneUpdate(request);

        assertTrue(response.isSuccess);

        val secondRequest = ConfirmPhoneNumberRequest(authToken, "12345")
        val secondResponse = client.confirmPhoneNumber(secondRequest);

        assertTrue(secondResponse.isSuccess);
        assertEquals(secondResponse.accountInfo!!.phoneNumber, newPhone)
    }

    @Test
    fun `Update Phone should fail when phone is not available`() {
        val userA = injectNamedSiteUser("a@a.com").user
        injectNamedSiteUser("b@b.com", "2222222222").user

        val authToken = devClient.createAuthToken(userA.username)

        val client = AccountUpdateClient(serverBaseUrl, JavaHttpClient())

        val newPhone = "2222222222"

        val request = RequestPhoneUpdateRequest(authToken, newPhone)
        val response = client.requestPhoneUpdate(request);

        assertTrue(response.isSuccess);

        val secondRequest = ConfirmPhoneNumberRequest(authToken, "12345")
        val secondResponse = client.confirmPhoneNumber(secondRequest);

        assertFalse(secondResponse.isSuccess);
    }

    @Test
    fun `attempting to authenticate with active devices maxed out should fail`() {
        val userA = injectNewSiteUser()
        val username = userA.user.username
        val maxDevices = devClient.getMaxDevices()

        for (i in 0..maxDevices-1)
            devClient.addDevice(username, 12345, true)

        val client = AuthenticationClient(serverBaseUrl, JavaHttpClient())

        val paramsApiResult = client.getParams(username)
        assertTrue(paramsApiResult.isSuccess, "Unable to fetch params")

        val csrfToken = paramsApiResult.params!!.csrfToken
        val authRequest = AuthenticationRequest(username, userA.keyVault.remotePasswordHash.hexify(), csrfToken, defaultRegistrationId, 0)

        val authResponse = client.auth(authRequest)

        assertFalse(authResponse.isSuccess, "Auth succeeded")
        assertTrue("too many registered devices" in authResponse.errorMessage!!.toLowerCase())
    }
}
