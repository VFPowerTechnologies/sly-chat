package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.defaultRemotePasswordHashParams
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.http.api.authentication.*
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.KeyVaultPersistenceManager
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.whispersystems.libsignal.util.KeyHelper
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthenticationServiceTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val password = "test-password"
        val keyVault = generateNewKeyVault(password)

        val authParams = defaultRemotePasswordHashParams()
    }

    val authenticationClient: AuthenticationAsyncClient = mock()
    val localAccountDirectory: LocalAccountDirectory = mock()

    val sessionDataPersistenceManager: SessionDataPersistenceManager = mock()
    val keyVaultPersistenceManager: KeyVaultPersistenceManager = mock()

    val registrationId = KeyHelper.generateRegistrationId(false)

    val authenticationService = AuthenticationService(
        authenticationClient,
        localAccountDirectory
    )

    val email = randomEmailAddress()
    val accountInfo = AccountInfo(randomUserId(), "name", email, "55555555", randomDeviceId())

    @Before
    fun before() {
        whenever(localAccountDirectory.getSessionDataPersistenceManager(any(), any(), any())).thenReturn(sessionDataPersistenceManager)
        whenever(localAccountDirectory.getKeyVaultPersistenceManager(any())).thenReturn(keyVaultPersistenceManager)
    }

    //setup mocks for successful remote auth
    fun withSuccessfulRemoteAuth(body: (AuthToken) -> Unit) {
        val authParams = AuthenticationParams(randomUUID(), authParams.serialize())
        val paramsResponse = AuthenticationParamsResponse(null, authParams)
        whenever(authenticationClient.getParams(email)).thenReturn(paramsResponse)

        val authToken = randomAuthToken()
        val authData = AuthenticationData(authToken, keyVault.serialize(), accountInfo)
        val authenticationResponse = AuthenticationResponse(null, authData)
        whenever(authenticationClient.auth(any())).thenReturn(authenticationResponse)

        body(authToken)
    }

    @Test
    fun `it should prefer local data when available (session data not available)`() {
        whenever(localAccountDirectory.findAccountFor(email)).thenReturn(accountInfo)
        whenever(sessionDataPersistenceManager.retrieveSync()).thenReturn(null)
        whenever(keyVaultPersistenceManager.retrieveSync(password)).thenReturn(keyVault)

        val result = authenticationService.auth(email, password, registrationId).get()

        assertEquals(accountInfo, result.accountInfo, "Invalid account info")
        assertNull(result.authToken, "Auth token should be null")
    }

    @Test
    fun `it should prefer local data when available (session data available)`() {
        val sessionData = SessionData(randomAuthToken())

        whenever(localAccountDirectory.findAccountFor(email)).thenReturn(accountInfo)
        whenever(sessionDataPersistenceManager.retrieveSync()).thenReturn(sessionData)
        whenever(keyVaultPersistenceManager.retrieveSync(password)).thenReturn(keyVault)

        val result = authenticationService.auth(email, password, registrationId).get()

        assertEquals(accountInfo, result.accountInfo, "Invalid account info")
        assertEquals(sessionData.authToken, result.authToken, "Invalid auth token")
    }

    @Test
    fun `it should switch to remote auth if no key vault is available`() {
        whenever(localAccountDirectory.findAccountFor(email)).thenReturn(accountInfo)
        whenever(keyVaultPersistenceManager.retrieveSync(password)).thenReturn(null)

        withSuccessfulRemoteAuth { authToken ->
            val result = authenticationService.auth(email, password, registrationId).get()

            assertEquals(accountInfo, result.accountInfo, "Invalid account info")
            assertEquals(authToken, result.authToken, "Auth token is invalid")
        }
    }

    @Test
    fun `it should switch to remote auth if no local data is available`() {
        whenever(localAccountDirectory.findAccountFor(any<String>())).thenReturn(null)

        withSuccessfulRemoteAuth { authToken ->
            val result = authenticationService.auth(email, password, registrationId).get()
            assertEquals(accountInfo, result.accountInfo, "Invalid account info")
            assertEquals(authToken, result.authToken, "Auth token is invalid")
        }
    }
}