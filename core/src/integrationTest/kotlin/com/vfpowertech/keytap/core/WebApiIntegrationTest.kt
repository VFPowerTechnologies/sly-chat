package com.vfpowertech.keytap.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.generateNewKeyVault
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.registration.RegistrationClient
import com.vfpowertech.keytap.core.http.api.registration.RegistrationInfo
import com.vfpowertech.keytap.core.http.api.registration.registrationRequestFromKeyVault
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

            if (!isDevServerSane())
                throw RuntimeException("Dev server behavior check failed")
        }
    }

    val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    val objectMapper = ObjectMapper()

    @Before
    fun before() {
        devClient.clear()
    }

    @Test
    fun `register request should succeed when given a unique username`() {
        val keyVaultPassword = "test"
        val keyVault = generateNewKeyVault(keyVaultPassword)
        val registrationInfo = RegistrationInfo("c@a.com", "name", "000-000-0000")
        val request = registrationRequestFromKeyVault(registrationInfo, keyVault)

        val client = RegistrationClient(serverBaseUrl, JavaHttpClient())
        val result = client.register(request)
        assertFalse(result.isError)
        assertNull(result.value!!.errorMessage)

        val expected = SiteUser(
            registrationInfo.email,
            keyVault.remotePasswordHash!!.hexify(),
            keyVault.remotePasswordHashParams!!.serialize(),
            keyVault.fingerprint,
            //FIXME whenever we fix metadata stuff
            mapOf(),
            keyVault.serialize()
        )

        assertEquals(listOf(expected), devClient.getUsers())
    }
}