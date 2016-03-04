package com.vfpowertech.keytap.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.axolotl.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.UnauthorizedException
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.http.api.contacts.ContactClient
import com.vfpowertech.keytap.core.http.api.contacts.ContactInfo
import com.vfpowertech.keytap.core.http.api.contacts.NewContactRequest
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyRetrieveClient
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyRetrieveRequest
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyStorageClient
import com.vfpowertech.keytap.core.http.api.prekeys.preKeyStorageRequestFromGeneratedPreKeys
import com.vfpowertech.keytap.core.http.api.prekeys.serializeOneTimePreKeys
import com.vfpowertech.keytap.core.http.api.prekeys.serializeSignedPreKey
import com.vfpowertech.keytap.core.http.api.registration.RegistrationClient
import com.vfpowertech.keytap.core.http.api.registration.RegistrationInfo
import com.vfpowertech.keytap.core.http.api.registration.registrationRequestFromKeyVault
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

data class GeneratedSiteUser(
    val user: SiteUser,
    val keyVault: KeyVault
)

class WebApiIntegrationTest {
    companion object {
        val serverBaseUrl = "http://localhost:8000"

        fun newSiteUser(registrationInfo: RegistrationInfo, password: String): GeneratedSiteUser {
            val keyVault = generateNewKeyVault(password)
            val serializedKeyVault = keyVault.serialize()

            val user = SiteUser(
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
            val siteUser = newSiteUser(RegistrationInfo(username, "a", "000-000-0000"), password).user

            devClient.addUser(siteUser)

            val users = devClient.getUsers()

            if (users != listOf(siteUser))
                throw DevServerInsaneException("Register functionality failed")

            val authToken = devClient.createAuthToken(username)

            val gotToken = devClient.getAuthToken(username)

            if (gotToken != authToken)
                throw DevServerInsaneException("Auth token functionality failed")

            val oneTimePreKeys = listOf("a", "b").sorted()
            devClient.addOneTimePreKeys(username, oneTimePreKeys)

            val gotPreKeys = devClient.getPreKeys(username).oneTimePreKeys.sorted()
            if (gotPreKeys != oneTimePreKeys)
                throw DevServerInsaneException("One-time prekey functionality failed")

            val signedPreKey = "s"
            devClient.setSignedPreKey(username, signedPreKey)

            if (devClient.getSignedPreKey(username) != signedPreKey)
                throw DevServerInsaneException("Signed prekey functionality failed")
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
    val objectMapper = ObjectMapper()

    fun injectNewSiteUser(): GeneratedSiteUser {
        val siteUser = newSiteUser(dummyRegistrationInfo, password)

        devClient.addUser(siteUser.user)

        return siteUser
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
        assertFalse(result.isError, "Api level error")
        assertNull(result.value!!.errorMessage)

        val expected = SiteUser(
            dummyRegistrationInfo.email,
            keyVault.remotePasswordHash.hexify(),
            keyVault.remotePasswordHashParams.serialize(),
            keyVault.fingerprint,
            dummyRegistrationInfo.name,
            dummyRegistrationInfo.phoneNumber,
            keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers())
    }

    @Test
    fun `register request should fail when a duplicate username is used`() {
        val siteUser = injectNewSiteUser().user
        val registrationInfo = RegistrationInfo(siteUser.username, "name", "0")

        val keyVault = generateNewKeyVault(password)
        val request = registrationRequestFromKeyVault(registrationInfo, keyVault)

        val client = RegistrationClient(serverBaseUrl, JavaHttpClient())
        val result = client.register(request)

        assertFalse(result.isError, "Api level error")
        assertNotNull(result.value, "Null value")
        assertNotNull(result.value!!.errorMessage, "Null error message")
        val errorMessage = result.value.errorMessage!!
        assertTrue(errorMessage.contains("taken"), "Invalid error message: $errorMessage}")
    }

    @Test
    fun `authentication request should success when given a valid username and password hash`() {
        val siteUser = injectNewSiteUser().user
        val username = siteUser.username

        val client = AuthenticationClient(serverBaseUrl, JavaHttpClient())

        val paramsApiResult = client.getParams(username)

        assertFalse(paramsApiResult.isError, "getParams: Api level error")
        assertNotNull(paramsApiResult.value, "getParams: value is null")
        assertNotNull(paramsApiResult.value!!.params, "getParams: params is null")

        val params = paramsApiResult.value.params!!

        val csrfToken = params.csrfToken
        val hashParams = HashDeserializers.deserialize(params.hashParams)

        val hash = hashPasswordWithParams(password, hashParams).hexify()

        val authRequest = AuthenticationRequest(username, hash, csrfToken)

        val authApiResult = client.auth(authRequest)
        assertFalse(authApiResult.isError, "auth: Api level error")
        assertNotNull(authApiResult.value, "auth: value is null")
        assertTrue(authApiResult.value!!.isSuccess, "auth: unsuccessful")

        val receivedSerializedKeyVault = authApiResult.value.data!!.keyVault

        assertEquals(siteUser.keyVault, receivedSerializedKeyVault)
    }

    @Test
    fun `prekey storage request should fail when an invalid auth token is used`() {
        val keyVault = generateNewKeyVault(password)
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)

        val request = preKeyStorageRequestFromGeneratedPreKeys("a", keyVault, generatedPreKeys)

        val client = PreKeyStorageClient(serverBaseUrl, JavaHttpClient())

        assertFailsWith(UnauthorizedException::class) {
            client.store(request)
        }
    }

    fun injectPreKeys(username: String, keyVault: KeyVault): GeneratedPreKeys {
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)
        devClient.addOneTimePreKeys(username, serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys))
        return generatedPreKeys
    }

    @Test
    fun `prekey storage request should store keys on the server when a valid auth token is used`() {
        val siteUser = injectNewSiteUser()
        val keyVault = siteUser.keyVault
        val username = siteUser.user.username

        val authToken = devClient.createAuthToken(username)
        val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 1)

        val request = preKeyStorageRequestFromGeneratedPreKeys(authToken, keyVault, generatedPreKeys)

        val client = PreKeyStorageClient(serverBaseUrl, JavaHttpClient())

        val apiResponse = client.store(request)
        assertFalse(apiResponse.isError)
        assertTrue(apiResponse.value!!.isSuccess)

        val preKeys = devClient.getPreKeys(username)

        val expectedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)

        assertEquals(expectedOneTimePreKeys, preKeys.oneTimePreKeys, "One-time prekeys don't match")
        assertEquals(expectedSignedPreKey, preKeys.signedPreKey, "Signed prekey doesn't match")
    }

    @Test
    fun `prekey retrieval should fail when an invalid auth token is used`() {
        val siteUser = injectNewSiteUser()

        val client = PreKeyRetrieveClient(serverBaseUrl, JavaHttpClient())
        assertFailsWith(UnauthorizedException::class) {
            client.retrieve(PreKeyRetrieveRequest("a", siteUser.user.username))
        }
    }

    //TODO more elaborate tests

    //TODO deal with identity key stuff
    @Ignore
    @Test
    fun `prekey retrieval should return the next available prekey when a valid auth token is used`() {
        val siteUser = injectNewSiteUser()
        val username = siteUser.user.username
        val generatedPreKeys = injectPreKeys(username, siteUser.keyVault)

        val authToken = devClient.createAuthToken(username)

        val client = PreKeyRetrieveClient(serverBaseUrl, JavaHttpClient())


        val apiResponse = client.retrieve(PreKeyRetrieveRequest(authToken, username))

        assertFalse(apiResponse.isError)
        assertTrue(apiResponse.value!!.isSuccess)

        assertNotNull(apiResponse.value.keyData, "No prekeys found")
        val preKeyData = apiResponse.value.keyData!!

        val serializedOneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
        val expectedSignedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)

        assertEquals(expectedSignedPreKey, preKeyData.signedPreKey, "Signed prekey doesn't match")

        assertTrue(serializedOneTimePreKeys.contains(preKeyData.preKey), "No matching one-time prekey found")
    }

    //TODO after I fix last resort key stuff
    @Ignore
    @Test
    fun `prekey retrieval should return the last resort key once no other keys are available`() {

    }

    @Test
    fun `new contact fetch from email should return the contact information`() {
        val siteUser = injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.username, siteUser.user.name, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactClient(serverBaseUrl, JavaHttpClient())

        val contactResponseEmail = client.fetchContactInfo(NewContactRequest(authToken, siteUser.user.username, null))

        val receivedEmailContactInfo = contactResponseEmail.value?.contactInfo!!

        assertFalse(contactResponseEmail.isError)
        assertEquals(contactDetails, receivedEmailContactInfo)
    }

    @Test
    fun `new contact fetch from phone should return the contact information`() {
        val siteUser = injectNewSiteUser()
        val authToken = devClient.createAuthToken(siteUser.user.username)

        val contactDetails = ContactInfo(siteUser.user.username, siteUser.user.name, siteUser.user.phoneNumber, siteUser.user.publicKey)

        val client = ContactClient(serverBaseUrl, JavaHttpClient())

        val contactResponse = client.fetchContactInfo(NewContactRequest(authToken, null, siteUser.user.phoneNumber))

        val receivedContactInfo = contactResponse.value?.contactInfo!!

        assertFalse(contactResponse.isError)
        assertEquals(contactDetails, receivedContactInfo)
    }
}
