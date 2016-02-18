package com.vfpowertech.keytap.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.HashDeserializers
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.hashPasswordWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationClient
import com.vfpowertech.keytap.core.http.api.authentication.AuthenticationRequest
import com.vfpowertech.keytap.core.http.api.registration.RegistrationClient
import com.vfpowertech.keytap.core.http.api.registration.RegistrationInfo
import com.vfpowertech.keytap.core.http.api.registration.registrationRequestFromKeyVault
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebApiIntegrationTest {
    companion object {
        val serverBaseUrl = "http://localhost:8000"

        fun newSiteUser(registrationInfo: RegistrationInfo, password: String): SiteUser {
            val keyVault = generateNewKeyVault(password)
            val serializedKeyVault = keyVault.serialize()

            return SiteUser(
                registrationInfo.email,
                keyVault.remotePasswordHash!!.hexify(),
                keyVault.remotePasswordHashParams!!.serialize(),
                keyVault.fingerprint,
                mapOf(),
                serializedKeyVault
            )
        }

        /** Short test for server dev functionality sanity. */
        private fun isDevServerSane(): Boolean {
            val devClient = DevClient(serverBaseUrl, JavaHttpClient())
            val password = "test"

            devClient.clear()
            val siteUser = newSiteUser(RegistrationInfo("a@a.com", "a", "0"), password)

            devClient.addUser(siteUser)

            val users = devClient.getUsers()

            devClient.clear()

            return users == listOf(siteUser)
        }

        //only run if server is up
        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            try {
                JavaHttpClient().get(serverBaseUrl)
            }
            catch (e: java.net.ConnectException) {
                Assume.assumeTrue(false)
            }

            try {
                if (!isDevServerSane())
                    throw DevServerInsaneException()
            }
            catch (e: RuntimeException) {
                throw DevServerInsaneException(e)
            }
        }
    }

    val dummyRegistrationInfo = RegistrationInfo("c@a.com", "name", "000-000-0000")
    val password = "test"

    val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    val objectMapper = ObjectMapper()

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
            keyVault.remotePasswordHash!!.hexify(),
            keyVault.remotePasswordHashParams!!.serialize(),
            keyVault.fingerprint,
            //FIXME whenever we fix metadata stuff
            mapOf(),
            keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers())
    }

    @Test
    fun `authentication request should success when given a valid username and password hash`() {
        val username = dummyRegistrationInfo.email
        val siteUser = newSiteUser(dummyRegistrationInfo, password)

        devClient.addUser(siteUser)

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
}