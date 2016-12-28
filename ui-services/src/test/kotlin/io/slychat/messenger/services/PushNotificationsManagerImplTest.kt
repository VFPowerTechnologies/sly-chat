package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.http.api.pushnotifications.*
import io.slychat.messenger.core.mapToMap
import io.slychat.messenger.core.randomInt
import io.slychat.messenger.core.randomSlyAddress
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.testutils.*
import nl.komponents.kovenant.deferred
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
    private val defaultUnregistrationToken = randomUnregistrationToken()

    @Before
    fun before() {
        whenever(pushNotificationsAsyncClient.register(any(), any())).thenResolve(RegisterResponse(defaultUnregistrationToken, null))
        whenever(pushNotificationsAsyncClient.unregister(any<UnregisterRequest>())).thenResolveUnit()
    }

    private fun createManager(
        isNetworkAvailable: Boolean = true,
        defaultToken: String? = null,
        registrations: Set<SlyAddress> = emptySet(),
        unregistrations: Set<SlyAddress> = emptySet()
    ): PushNotificationsManagerImpl {
        appConfigService.withEditor {
            pushNotificationsToken = defaultToken
            pushNotificationsRegistrations = registrations.mapToMap { it to defaultUnregistrationToken }
            pushNotificationsUnregistrations = unregistrations.mapToMap { it to defaultUnregistrationToken }
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

    private fun randomUnregistrationToken(): String = "unregistration-token-${randomInt(0, 1000)}"

    private fun randomToken(): String = "token-${randomInt(0, 1000)}"

    private fun login() {
        userSessionAvailable.onNext(userComponent)
    }

    private fun enableNetwork() {
        networkAvailability.onNext(true)
    }

    private fun newToken(): String {
        val token = randomToken()
        tokenUpdates.onNext(token)
        return token
    }

    private fun assertSuccessfulRegistration(token: String) {
        val request = RegisterRequest(token, pushNotificationService, false)

        verify(pushNotificationsAsyncClient).register(any(), eq(request))

        val address = userComponent.userLoginData.address

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should add account to registrations list on success")
            containsEntry(address, defaultUnregistrationToken)
        }
    }

    private fun assertNoRegistrationAttempt() {
        verify(pushNotificationsAsyncClient, never()).register(any(), any())
    }

    private fun assertNoUnregistrationAttempt() {
        verify(pushNotificationsAsyncClient, never()).unregister(any<UnregisterRequest>())
    }

    private fun assertNotRemovedFromUnregistrations(address: SlyAddress) {
        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should contain the address")
            containsKey(address)
        }
    }

    private fun assertRemovedFromUnregistrations(address: SlyAddress) {
        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should not contain the unregistered address")
            doesNotContainKey(address)
        }
    }

    private fun assertSuccessfulUnregistration(address: SlyAddress, unregistrationToken: String) {
        val request = UnregisterRequest(address, unregistrationToken)

        verify(pushNotificationsAsyncClient).unregister(request)

        assertRemovedFromUnregistrations(address)
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
        val manager = createManager(registrations = setOf(randomSlyAddress()))

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
            registrations = setOf(userComponent.userLoginData.address)
        )

        login()

        val token = newToken()

        assertSuccessfulRegistration(token)
    }

    @Test
    fun `it should not attempt to register when receiving the same token value`() {
        val token = randomToken()

        val address = userComponent.userLoginData.address
        val manager = createManager(
            defaultToken = token,
            registrations = setOf(address)
        )

        login()

        tokenUpdates.onNext(token)

        verify(pushNotificationsAsyncClient, never()).register(any(), any())

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should not do anything when token doesn't change")
            containsEntry(address, defaultUnregistrationToken)
        }
    }

    @Test
    fun `it should move all current registrations to unregistrations when receiving a new null token value`() {
        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(randomSlyAddress(), randomSlyAddress()),
            unregistrations = setOf(randomSlyAddress()),
            isNetworkAvailable = false
        )

        val registrations = appConfigService.pushNotificationsRegistrations

        val oldUnregistrations = appConfigService.pushNotificationsUnregistrations

        tokenUpdates.onNext(null)

        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should contain all previous registrations")
            containsAllEntriesOf(registrations)
        }

        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should contain all previous unregistrations")
            containsAllEntriesOf(oldUnregistrations)
        }

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Registrations list should be empty")
            isEmpty()
        }
    }

    @Test
    fun `it should attempt to perform unregistrations if network is available and a new null token is received`() {
        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(address),
            isNetworkAvailable = true
        )

        tokenUpdates.onNext(null)

        assertSuccessfulUnregistration(address, defaultUnregistrationToken)
    }

    @Test
    fun `it should not attempt to register on login if already registered`() {
        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(userComponent.userLoginData.address)
        )

        login()

        assertNoRegistrationAttempt()
    }

    @Test
    fun `it should not attempt to register when network becomes available if already registered`() {
        val manager = createManager(
            isNetworkAvailable = false,
            defaultToken = randomToken(),
            registrations = setOf(userComponent.userLoginData.address)
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
            registrations = setOf(userComponent.userLoginData.address)
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

        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = token,
            isNetworkAvailable = false,
            registrations = setOf(address)
        )

        manager.unregister(address)

        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should add the given address to the unregistration list")
            containsEntry(address, defaultUnregistrationToken)
        }
    }

    @Test
    fun `unregister should remove the address from the registrations list when called`() {
        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = false,
            registrations = setOf(address)
        )

        manager.unregister(address)

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should remove the address from the registrations list")
            doesNotContainKey(address)
        }
    }

    @Test
    fun `only one of registration or unregistration should be active at once`() {
        val registrationDeferred = deferred<RegisterResponse, Exception>()
        val unregistrationDeferred = deferred<Unit, Exception>()

        whenever(pushNotificationsAsyncClient.register(any(), any())).thenReturn(registrationDeferred.promise)
        whenever(pushNotificationsAsyncClient.unregister(any())).thenReturn(unregistrationDeferred.promise)

        val address = randomSlyAddress()

        val token = randomToken()

        val manager = createManager(
            defaultToken = token,
            isNetworkAvailable = true,
            registrations = setOf(address)
        )

        //begin registration
        login()

        //queue unregistration
        manager.unregister(address)

        verify(pushNotificationsAsyncClient, never()).unregister(any())
    }

    @Test
    fun `it should process pending registrations once all unregistrations have completed`() {
        val registrationDeferred = deferred<RegisterResponse, Exception>()
        val unregistrationDeferred = deferred<Unit, Exception>()

        whenever(pushNotificationsAsyncClient.register(any(), any())).thenReturn(registrationDeferred.promise)
        whenever(pushNotificationsAsyncClient.unregister(any())).thenReturn(unregistrationDeferred.promise)

        val unregistrationAddress = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = true,
            registrations = setOf(unregistrationAddress)
        )

        manager.unregister(unregistrationAddress)

        login()

        verify(pushNotificationsAsyncClient, never()).register(any(), any())

        unregistrationDeferred.resolve(Unit)

        verify(pushNotificationsAsyncClient).register(any(), any())
    }

    @Test
    fun `it should process pending unregistrations once all registration have completed`() {
        val registrationDeferred = deferred<RegisterResponse, Exception>()
        val unregistrationDeferred = deferred<Unit, Exception>()

        whenever(pushNotificationsAsyncClient.register(any(), any())).thenReturn(registrationDeferred.promise)
        whenever(pushNotificationsAsyncClient.unregister(any())).thenReturn(unregistrationDeferred.promise)

        val unregistrationAddress = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = true,
            registrations = setOf(unregistrationAddress)
        )

        login()

        manager.unregister(unregistrationAddress)

        verify(pushNotificationsAsyncClient, never()).unregister(any())

        registrationDeferred.resolve(RegisterResponse(defaultUnregistrationToken, null))

        verify(pushNotificationsAsyncClient).unregister(any())
    }

    //TODO keeping going on error test

    //eg: user clicks to stop receiving notifications while offline, then they do a local login afterwards without
    //the unregistration having actually occured
    //in this case we just cancel the pending unregistration
    @Test
    fun `login with a pending unregistration should return address to registration list`() {
        val address = userComponent.userLoginData.address

        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = true,
            unregistrations = setOf(address)
        )

        login()

        assertThat(appConfigService.pushNotificationsUnregistrations).apply {
            describedAs("Should remove address from unregistrations")
            doesNotContainKey(address)
        }

        assertThat(appConfigService.pushNotificationsRegistrations).apply {
            describedAs("Should readd")
            containsKey(address)
        }
    }

    @Test
    fun `it should remove the address from unregistrations on successful unregistration`() {
        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(address)
        )

        manager.unregister(address)

        assertRemovedFromUnregistrations(address)
    }

    @Test
    fun `it should not remove the address from unregistrations on unregistration failure`() {
        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(address)
        )

        whenever(pushNotificationsAsyncClient.unregister(any<UnregisterRequest>())).thenReject(TestException())

        manager.unregister(address)

        assertNotRemovedFromUnregistrations(address)
    }

    @Test
    fun `unregister should attempt to unregister the given address if network is available`() {
        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            registrations = setOf(address)
        )

        manager.unregister(address)

        assertSuccessfulUnregistration(address, defaultUnregistrationToken)
    }

    @Test
    fun `unregister should not attempt to unregister the given address if network is unavailable`() {
        val address = randomSlyAddress()

        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = false,
            registrations = setOf(address)
        )

        manager.unregister(address)

        assertNoUnregistrationAttempt()
    }

    @Test
    fun `it should attempt to unregister tokens when the network becomes available`() {
        val address = randomSlyAddress()

        val token = randomToken()

        val manager = createManager(
            defaultToken = token,
            isNetworkAvailable = false,
            registrations = setOf(address)
        )

        manager.unregister(address)

        enableNetwork()

        assertSuccessfulUnregistration(address, defaultUnregistrationToken)
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
            containsAll(addresses.map { UnregisterRequest(it, defaultUnregistrationToken) })
        }
    }

    //shouldn't occur
    @Test
    fun `it should not do anything if unregistered is called for a non-registered account`() {
        val manager = createManager(
            defaultToken = randomToken(),
            isNetworkAvailable = true
        )

        manager.unregister(randomSlyAddress())
    }
}