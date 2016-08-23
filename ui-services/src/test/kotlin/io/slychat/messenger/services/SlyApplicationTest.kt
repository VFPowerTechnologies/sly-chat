package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.InstallationData
import io.slychat.messenger.core.persistence.StartupInfoPersistenceManager
import io.slychat.messenger.core.randomAccountInfo
import io.slychat.messenger.core.randomDeviceId
import io.slychat.messenger.core.randomRegistrationId
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import rx.observers.TestSubscriber
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

class SlyApplicationTest {
    companion object {
        @ClassRule
        @JvmField
        val kovenantRule = KovenantTestModeRule()
    }

    val appComponent = MockApplicationComponent()

    val platformContactsUpdated: PublishSubject<Unit> = PublishSubject.create()
    val relayOnlineStatus: BehaviorSubject<Boolean> = BehaviorSubject.create(false)
    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()

    val startupInfoPersistenceManager: StartupInfoPersistenceManager = mock()

    @Before
    fun before() {
        whenever(appComponent.installationDataPersistenceManager.retrieve()).thenReturn(InstallationData.generate())

        whenever(appComponent.platformContacts.contactsUpdated).thenReturn(platformContactsUpdated)

        whenever(appComponent.localAccountDirectory.getStartupInfoPersistenceManager()).thenReturn(startupInfoPersistenceManager)

        val userComponent = appComponent.userComponent

        //used in backgroundInitialization
        whenever(userComponent.sessionDataPersistenceManager.store(any())).thenReturn(Unit)
        whenever(startupInfoPersistenceManager.store(any())).thenReturn(Unit)
        whenever(userComponent.persistenceManager.initAsync()).thenReturn(Unit)
        whenever(userComponent.accountInfoManager.update(any())).thenReturn(Unit)
        whenever(userComponent.keyVaultPersistenceManager.store(any())).thenReturn(Unit)
        whenever(userComponent.relayClientManager.onlineStatus).thenReturn(relayOnlineStatus)
        whenever(userComponent.relayClientManager.events).thenReturn(relayEvents)
        whenever(userComponent.messageCipherService.updateSelfDevices(any())).thenReturn(Unit)
        whenever(userComponent.contactsService.addContact(any())).thenReturn(true)

        //used in finalizeInitialization
    }

    fun createApp(): SlyApplication {
        val app = SlyApplication()

        return app
    }

    fun assertSuccessfulLogin(testSubscriber: TestSubscriber<LoginEvent>) {
        var successSeen = false

        testSubscriber.onNextEvents.forEach {
            when (it) {
                is LoginEvent.LoginFailed -> throw AssertionError("Login failure: ${it.errorMessage}", it.exception)
                is LoginEvent.LoggedIn -> successSeen = true
            }
        }

        if (!successSeen)
            throw AssertionError("No LoggedIn event found, and no LoginFailed event found")
    }

    fun doLogin(app: SlyApplication, rememberMe: Boolean = false) {
        val loginEventSubscriber = TestSubscriber<LoginEvent>()

        app.loginEvents.subscribe(loginEventSubscriber)
        try {

            app.login("email", "password", rememberMe)
            assertSuccessfulLogin(loginEventSubscriber)
        }
        finally {
            loginEventSubscriber.unsubscribe()
        }
    }

    @Ignore
    @Test
    fun `it should create new installation data during initialization if no data exists`() { TODO() }

    @Ignore
    @Test
    fun `it should create new installation data during initialization if data is corrupted`() { TODO() }

    @Ignore
    @Test
    fun `it should use existing installation data during initialization if data is present`() { TODO() }

    @Ignore
    @Test
    fun `it should attempt to login automatically after basic initialization`() { TODO() }

    fun authWithOtherDevices(otherDevices: List<DeviceInfo>) {
        val accountInfo = randomAccountInfo()

        val authResult = AuthResult(null, MockUserComponent.keyVault, accountInfo, otherDevices)

        whenever(appComponent.authenticationService.auth(any(), any(), any())).thenReturn(authResult)
        val app = createApp()

        app.init(appComponent)

        doLogin(app)
    }

    @Test
    fun `MessageCipherService should be initialized before use`() {
        val otherDevices = listOf(DeviceInfo(randomDeviceId(), randomRegistrationId()))
        authWithOtherDevices(otherDevices)

        val messageCipherService = appComponent.userComponent.messageCipherService
        val order = inOrder(messageCipherService)

        order.verify(messageCipherService).start()
        order.verify(messageCipherService).updateSelfDevices(any())
    }

    @Test
    fun `our own account must be added to the address book before MessageCipherService is called`() {
        val otherDevices = listOf(DeviceInfo(randomDeviceId(), randomRegistrationId()))
        authWithOtherDevices(otherDevices)

        val messageCipherService = appComponent.userComponent.messageCipherService
        val contactsService = appComponent.userComponent.contactsService

        val order = inOrder(contactsService, messageCipherService)

        order.verify(contactsService).addContact(any())
        order.verify(messageCipherService).updateSelfDevices(any())
    }

    @Test
    fun `it should update the self devices list during user initialization`() {
        val otherDevices = listOf(DeviceInfo(randomDeviceId(), randomRegistrationId()))
        authWithOtherDevices(otherDevices)
        verify(appComponent.userComponent.messageCipherService).updateSelfDevices(otherDevices)
    }
}
