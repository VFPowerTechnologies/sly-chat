package io.slychat.messenger.core.integration.web

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.SerializedKeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.registration.RegistrationClient
import io.slychat.messenger.core.http.api.registration.RegistrationInfo
import io.slychat.messenger.core.http.api.registration.registrationRequestFromKeyVault
import io.slychat.messenger.core.integration.utils.DevClient
import io.slychat.messenger.core.integration.utils.SiteUser
import io.slychat.messenger.core.integration.utils.SiteUserManagement
import io.slychat.messenger.core.integration.utils.serverBaseUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebApiRegisterTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)
    private val dummyRegistrationInfo = userManagement.dummyRegistrationInfo
    private val password = userManagement.defaultPassword

    @Before
    fun before() {
        devClient.clear()
    }

    @Test
    fun `register request should succeed when given a unique username`() {
        val keyVault = generateNewKeyVault(password)
        val request = registrationRequestFromKeyVault(dummyRegistrationInfo, keyVault, password)

        val client = RegistrationClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())
        val result = client.register(request)
        assertNull(result.validationErrors)
        assertNull(result.errorMessage)

        val user = assertNotNull(devClient.getUser(dummyRegistrationInfo.email), "Missing user")

        val objectMapper = ObjectMapper()
        val serializedKeyVault = objectMapper.readValue(request.serializedKeyVault, SerializedKeyVault::class.java)
        val hashParams = objectMapper.readValue(request.hashParams, HashParams::class.java)

        val expected = SiteUser(
            //don't care about the id
            user.id,
            dummyRegistrationInfo.email,
            hashParams,
            keyVault.fingerprint,
            dummyRegistrationInfo.name,
            dummyRegistrationInfo.phoneNumber,
            serializedKeyVault
        )

        assertThat(user).apply {
            isEqualToComparingFieldByField(expected)
        }
    }

    @Test
    fun `register request should fail when a duplicate username is used`() {
        val siteUser = userManagement.injectNewSiteUser().user
        val registrationInfo = RegistrationInfo(siteUser.email, "name", "0")

        val keyVault = generateNewKeyVault(password)
        val request = registrationRequestFromKeyVault(registrationInfo, keyVault, password)

        val client = RegistrationClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())
        val result = client.register(request)

        assertNotNull(result.errorMessage, "Null error message")
        val errorMessage = result.errorMessage!!
        assertTrue(errorMessage.contains("taken"), "Invalid error message: $errorMessage}")
    }
}