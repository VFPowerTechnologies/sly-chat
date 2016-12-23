package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.http.api.pushnotifications.*
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.testutils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import kotlin.test.assertEquals

class PushNotificationsManagerImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    private val pushNotificationsAsyncClient: PushNotificationsAsyncClient = mock()
    private val networkAvailability = BehaviorSubject.create<Boolean>()
    private val userComponent = MockUserComponent()
    private val userSessionAvailable = BehaviorSubject.create<UserComponent?>()
    private val tokenUpdates = PublishSubject.create<String>()
    private val appConfigService = AppConfigService(DummyConfigBackend())
    private val pushNotificationService = PushNotificationService.GCM

    @Before
    fun before() {
        whenever(pushNotificationsAsyncClient.register(any(), any())).thenResolve(RegisterResponse(null))
        whenever(pushNotificationsAsyncClient.unregister(any<UnregisterRequest>())).thenResolveUnit()
        whenever(pushNotificationsAsyncClient.unregister(any<UserCredentials>())).thenResolveUnit()
    }

    private fun createManager(
        isNetworkAvailable: Boolean = true,
        defaultToken: String? = null,
        registrations: Set<UserId> = emptySet(),
        unregistrations: Set<SlyAddress> = emptySet()
    ): PushNotificationsManagerImpl {
        appConfigService.withEditor {
            pushNotificationsToken = defaultToken
            pushNotificationsRegistrations = registrations
            pushNotificationsUnregistrations = unregistrations
        }

        networkAvailability.onNext(isNetworkAvailable)

        return PushNotificationsManagerImpl(
            tokenUpdates,
            userSessionAvailable,
            networkAvailability,
            pushNotificationService,
            appConfigService,
            pushNotificationsAsyncClient
        )
    }

    private fun randomToken(): String = randomUUID()

    private fun login() {
        userSessionAvailable.onNext(userComponent)
    }

    private fun enableNetwork() {
        networkAvailability.onNext(true)
    }

    private fun assertSuccessfulRegistration(token: String) {
        val request = RegisterRequest(token, pushNotificationService, false)

        verify(pushNotificationsAsyncClient).register(any(), eq(request))

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should add account to registrations list on success")
            contains(userComponent.userLoginData.userId)
        }
    }

    private fun assertNoRegistrationAttempt() {
        verify(pushNotificationsAsyncClient, never()).register(any(), any())
    }

    @Test
    fun `it should update the currently stored token when receiving a new token`() {
        val manager = createManager()

        val token = randomToken()

        tokenUpdates.onNext(token)

        assertEquals(token, appConfigService.pushNotificationsToken, "Token not updated")
    }

    @Test
    fun `it should reset the sent list when receiving a new token`() {
        val manager = createManager(registrations = setOf(randomUserId()))

        val token = randomToken()

        tokenUpdates.onNext(token)

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should clear registrations")
            isEmpty()
        }
    }

    @Test
    fun `it should attempt to register a token if already logged in and no previous token was available`() {
        val manager = createManager()

        login()

        val token = randomToken()

        tokenUpdates.onNext(token)

        assertSuccessfulRegistration(token)
    }

    @Test
    fun `it should attempt to register a new token if already logged in and was previously registered`() {
        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(userComponent.userLoginData.userId)
        )

        login()

        val token = randomToken()

        tokenUpdates.onNext(token)

        assertSuccessfulRegistration(token)
    }

    @Test
    fun `it should not attempt to register when receiving the same token value`() {
        val token = randomToken()

        val userId = userComponent.userLoginData.userId
        val manager = createManager(
            defaultToken = token,
            registrations = setOf(userId)
        )

        login()

        tokenUpdates.onNext(token)

        verify(pushNotificationsAsyncClient, never()).register(any(), any())

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should not do anything when token doesn't change")
            contains(userId)
        }
    }

    @Test
    fun `it should not attempt to register on login if already registered`() {
        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(userComponent.userLoginData.userId)
        )

        login()

        assertNoRegistrationAttempt()
    }

    @Test
    fun `it should not attempt to register when network becomes available if already registered`() {
        val manager = createManager(
            isNetworkAvailable = false,
            defaultToken = randomToken(),
            registrations = setOf(userComponent.userLoginData.userId)
        )

        login()

        enableNetwork()

        assertNoRegistrationAttempt()
    }

    @Test
    fun `it should do nothing on login if the token requires registering but no network is available`() {
        val manager = createManager(
            isNetworkAvailable = false,
            defaultToken = randomToken(),
            registrations = setOf(userComponent.userLoginData.userId)
        )

        login()

        assertNoRegistrationAttempt()
    }

    @Test
    fun `it should not do anything on login if no token is available`() {
        val manager = createManager()

        login()

        verify(pushNotificationsAsyncClient, never()).register(any(), any())
    }

    @Test
    fun `it should attempt to register the token on login if the network becomes available and the token is not already registered`() {
        val token = randomToken()

        val manager = createManager(isNetworkAvailable = false, defaultToken = token)

        login()

        enableNetwork()

        assertSuccessfulRegistration(token)
    }

    @Test
    fun `it should attempt to register a pending token if the network is available when login occurs`() {
        val token = randomToken()

        val manager = createManager(
            defaultToken = token
        )

        login()

        assertSuccessfulRegistration(token)
    }

    //TODO retry policies on error; need to add some kinda timer

    @Test
    fun `unregister should add the given address to the unregistrations list`() {
        val token = randomToken()

        val manager = createManager(defaultToken = token, isNetworkAvailable = false)

        val address = randomSlyAddress()

        manager.unregister(address)

        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should add the given address to the unregistration list")
            contains(address)
        }
    }

    @Test
    fun `unregister should remove the address from the registrations list when called`() {
        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = false,
            registrations = setOf(address.id)
        )

        manager.unregister(address)

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should remove the address from the registrations list")
            doesNotContain(address.id)
        }
    }

    @Test
    fun `only one of registration or unregistration should be active at once`() {
        TODO()
    }

    //eg: user clicks to stop receiving notifications while offline, then they do a local login afterwards without
    //the unregistration having actually occured
    //in this case we just cancel the pending unregistration
    @Test
    fun `login with a pending unregistration should return address to registration list`() {
        TODO()
    }

    //shouldn't occur?
    @Test
    fun `unregister should do nothing if no token is available`() {
        val manager = createManager()

        val address = randomSlyAddress()

        manager.unregister(address)

        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should not add the given address to the unregistration list")
            doesNotContain(address)
        }
    }

    private fun assertNoUnregistrationAttempt() {
        verify(pushNotificationsAsyncClient, never()).unregister(any<UnregisterRequest>())
    }

    private fun assertNotRemovedFromUnregistrations(address: SlyAddress) {
        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should contain the address")
            contains(address)
        }
    }

    private fun assertRemovedFromUnregistrations(address: SlyAddress) {
        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should not contain the unregistered address")
            doesNotContain(address)
        }
    }

    private fun assertSuccessfulUnregistration(address: SlyAddress, token: String) {
        val request = UnregisterRequest(address, token)

        verify(pushNotificationsAsyncClient).unregister(request)

        assertRemovedFromUnregistrations(address)
    }

    @Test
    fun `it should remove the address from unregistrations on successful unregistration`() {
        val manager = createManager(
            defaultToken = randomToken()
        )

        val address = randomSlyAddress()

        manager.unregister(address)

        assertRemovedFromUnregistrations(address)
    }

    @Test
    fun `it should not remove the address from unregistrations on unregistration failure`() {
        val manager = createManager(
            defaultToken = randomToken()
        )

        whenever(pushNotificationsAsyncClient.unregister(any<UnregisterRequest>())).thenReject(TestException())

        val address = randomSlyAddress()

        manager.unregister(address)

        assertNotRemovedFromUnregistrations(address)
    }

    @Test
    fun `unregister should attempt to unregister the given address if network is available`() {
        val token = randomToken()

        val manager = createManager(
            defaultToken = token
        )

        val address = randomSlyAddress()

        manager.unregister(address)

        assertSuccessfulUnregistration(address, token)
    }

    @Test
    fun `unregister should not attempt to unregister the given address if network is unavailable`() {
        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = false
        )

        manager.unregister(randomSlyAddress())

        assertNoUnregistrationAttempt()
    }

    @Test
    fun `it should attempt to unregister tokens when the network becomes available`() {
        val token = randomToken()
        val manager = createManager(
            defaultToken = token,
            isNetworkAvailable = false
        )

        val address = randomSlyAddress()

        manager.unregister(address)

        enableNetwork()

        assertSuccessfulUnregistration(address, token)
    }

    @Test
    fun `it should unregister all registered tokens`() {
        val addresses = setOf(randomSlyAddress(), randomSlyAddress())

        val token = randomToken()

        val manager = createManager(
            defaultToken = token,
            isNetworkAvailable = false,
            unregistrations = addresses
        )

        enableNetwork()

        val captor = argumentCaptor<UnregisterRequest>()

        verify(pushNotificationsAsyncClient, times(2)).unregister(capture(captor))

        assertThat(captor.allValues).apply {
            describedAs("Should call unregister for all tokens")
            containsAll(addresses.map { UnregisterRequest(it, token) })
        }
    }
}