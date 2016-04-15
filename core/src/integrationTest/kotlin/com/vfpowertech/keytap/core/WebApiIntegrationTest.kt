package com.vfpowertech.keytap.core

import com.vfpowertech.keytap.core.crypto.*
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.UnauthorizedException
import com.vfpowertech.keytap.core.http.api.accountUpdate.AccountUpdateClient
import com.vfpowertech.keytap.core.http.api.accountUpdate.UpdatePhoneRequest
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

class WebApiIntegrationTest {
    companion object {
        val serverBaseUrl = "http://localhost:8000"

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

            val contactsA = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.username))
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

    fun injectSiteUser(registrationInfo: RegistrationInfo): GeneratedSiteUser {
        val siteUser = newSiteUser(registrationInfo, password)

        devClient.addUser(siteUser.user)

        return siteUser
    }

    fun injectNamedSiteUser(username: String): GeneratedSiteUser {
        val registrationInfo = RegistrationInfo(username, "name", "000-000-0000")
        return injectSiteUser(registrationInfo)
    }

    fun injectNewSiteUser(): GeneratedSiteUser {
        return injectSiteUser(dummyRegistrationInfo)
    }

    @Before
    fun before() {
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
    fun `authentication request should success when given a valid username and password hash`() {
        val siteUser = injectNewSiteUser().user
        val username = siteUser.username

        val client = AuthenticationClient(serverBaseUrl, JavaHttpClient())

        val paramsApiResult = client.getParams(username)

        assertNotNull(paramsApiResult.params, "getParams: params is null")

        val params = paramsApiResult.params!!

        val csrfToken = params.csrfToken
        val hashParams = HashDeserializers.deserialize(params.hashParams)

        val hash = hashPasswordWithParams(password, hashParams).hexify()

        val authRequest = AuthenticationRequest(username, hash, csrfToken)

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

    fun injectPreKeys(username: String, keyVault: KeyVault): GeneratedPreKeys {
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        devClient.addOneTimePreKeys(username, serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys))
        devClient.setSignedPreKey(username, serializeSignedPreKey(generatedPreKeys.signedPreKey))
        return generatedPreKeys
    }

    fun injectLastResortPreKey(username: String): PreKeyRecord {
        val lastResortPreKey = generateLastResortPreKey()
        devClient.setLastResortPreKey(username, serializeOneTimePreKeys(listOf(lastResortPreKey))[0])
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
            client.retrieve(PreKeyRetrievalRequest("a", siteUser.user.username))
        }
    }

    //TODO more elaborate tests

    @Test
    fun `prekey retrieval should return the next available prekey when a valid auth token is used`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username
        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault)

        val authToken = devClient.createAuthToken(username)

        val client = PreKeyRetrievalClient(serverBaseUrl, JavaHttpClient())

        val response = client.retrieve(PreKeyRetrievalRequest(authToken, username))

        assertTrue(response.isSuccess)

        assertNotNull(response.keyData, "No prekeys found")
        val preKeyData = response.keyData!!

        val serializedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)

        assertEquals(expectedSignedPreKey, preKeyData.signedPreKey, "Signed prekey doesn't match")

        assertTrue(serializedOneTimePreKeys.contains(preKeyData.preKey), "No matching one-time prekey found")
    }

    fun assertNextPreKeyIs(username: String, authToken: String, expected: PreKeyRecord, signedPreKey: SignedPreKeyRecord) {
        val client = PreKeyRetrievalClient(serverBaseUrl, JavaHttpClient())

        val response = client.retrieve(PreKeyRetrievalRequest(authToken, username))

        assertTrue(response.isSuccess)

        assertNotNull(response.keyData, "No prekeys found")
        val preKeyData = response.keyData!!

        val expectedOneTimePreKeys = serializePreKey(expected)
        val expectedSignedPreKey = serializeSignedPreKey(signedPreKey)

        assertEquals(expectedSignedPreKey, preKeyData.signedPreKey, "Signed prekey doesn't match")

        assertEquals(expectedOneTimePreKeys, preKeyData.preKey, "One-time prekey doesn't match")
    }

    @Test
    fun `prekey retrieval should return the last resort key once no other keys are available`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault)
        val lastResortPreKey = injectLastResortPreKey(username)

        assertNextPreKeyIs(username, authToken, generatedPreKeys.oneTimePreKeys[0], generatedPreKeys.signedPreKey)
        assertNextPreKeyIs(username, authToken, lastResortPreKey, generatedPreKeys.signedPreKey)
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

        val encryptedContacts = encryptRemoteContactEntries(siteUser.keyVault, listOf(contactUser.user.username))
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
        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.username))
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

        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userB.user.username))
        val bContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userC.user.username))

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

        val aContacts = encryptRemoteContactEntries(userA.keyVault, listOf(userC.user.username, userB.user.username))

        devClient.addContacts(userA.user.username, aContacts)

        val client = ContactListClient(serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(userA.user.username)

        val request = RemoveContactsRequest(authToken, listOf(aContacts[0].hash))
        client.removeContacts(request)

        val contacts = devClient.getContactList(userA.user.username)

        assertContactListEquals(listOf(aContacts[1]), contacts)
    }

    @Test
    fun `Update Phone should succeed when right password is provided`() {
        val user = newSiteUser(RegistrationInfo("a@a.com", "a", "000-000-0000"), password)

        devClient.addUser(user.user)

        val client = AccountUpdateClient(serverBaseUrl, JavaHttpClient())

        val request = UpdatePhoneRequest(user.user.username, user.user.passwordHash, "111-111-1111")
        val response = client.updatePhone(request)

        assertTrue(response.isSuccess)

        val expected = SiteUser(
                user.user.id,
                user.user.username,
                user.keyVault.remotePasswordHash.hexify(),
                user.keyVault.remotePasswordHashParams.serialize(),
                user.keyVault.fingerprint,
                user.user.name,
                "111-111-1111",
                user.keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers());
    }

    @Test
    fun `Update Phone should fail when wrong password is provided`() {
        val user = newSiteUser(RegistrationInfo("a@a.com", "a", "000-000-0000"), password)

        devClient.addUser(user.user)

        val client = AccountUpdateClient(serverBaseUrl, JavaHttpClient())

        val request = UpdatePhoneRequest(user.user.username, "wrongPassword", "111-111-1111")
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
}
