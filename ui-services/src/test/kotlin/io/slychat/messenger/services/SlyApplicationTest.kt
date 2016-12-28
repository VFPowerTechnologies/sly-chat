package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.defaultRemotePasswordHashParams
import io.slychat.messenger.core.crypto.randomRegistrationId
import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.AccountLocalInfo
import io.slychat.messenger.core.persistence.InstallationData
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.StartupInfoPersistenceManager
import io.slychat.messenger.core.randomAccountInfo
import io.slychat.messenger.core.randomAuthToken
import io.slychat.messenger.core.randomDeviceId
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.core.relay.RelayClientState
import io.slychat.messenger.services.auth.AuthResult
import io.slychat.messenger.services.config.AppConfig
import io.slychat.messenger.testutils.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import org.mockito.exceptions.verification.NeverWantedButInvoked
import rx.observers.TestSubscriber
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlyApplicationTest {
    companion object {
        @ClassRule
        @JvmField
        val kovenantRule = KovenantTestModeRule()

        val accountLocalInfo = AccountLocalInfo.generate(defaultRemotePasswordHashParams())
    }

    val accountInfo = randomAccountInfo()

    val appComponent = MockApplicationComponent()
    val userComponent = appComponent.userComponent

    val platformContactsUpdated: PublishSubject<Unit> = PublishSubject.create()
    val relayOnlineStatus: BehaviorSubject<Boolean> = BehaviorSubject.create(false)
    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()
    val clockDiffUpdates: PublishSubject<Long> = PublishSubject.create()

    val startupInfoPersistenceManager: StartupInfoPersistenceManager = mock()

    val remotePasswordHash = emptyByteArray()

    @Before
    fun before() {
        whenever(appComponent.installationDataPersistenceManager.retrieve()).thenResolve(InstallationData.generate())

        whenever(appComponent.platformContacts.contactsUpdated).thenReturn(platformContactsUpdated)

        whenever(appComponent.localAccountDirectory.getStartupInfoPersistenceManager(any())).thenReturn(startupInfoPersistenceManager)

        whenever(appComponent.appConfigBackend.read<AppConfig>(any())).thenResolve(null)

        val userComponent = appComponent.userComponent

        //used in backgroundInitialization
        whenever(userComponent.sessionDataManager.update(any())).thenResolve(Unit)
        whenever(startupInfoPersistenceManager.store(any())).thenResolve(Unit)
        whenever(userComponent.persistenceManager.initAsync()).thenResolve(Unit)
        whenever(userComponent.accountInfoManager.update(any())).thenResolve(Unit)
        whenever(userComponent.keyVaultPersistenceManager.store(any())).thenResolve(Unit)
        whenever(userComponent.relayClientManager.onlineStatus).thenReturn(relayOnlineStatus)
        whenever(userComponent.relayClientManager.events).thenReturn(relayEvents)
        whenever(userComponent.messageCipherService.updateSelfDevices(any())).thenResolve(Unit)
        whenever(userComponent.contactsService.addSelf(any())).thenResolve(Unit)
        whenever(userComponent.messengerService.broadcastNewDevice(any())).thenResolve(Unit)
        whenever(userComponent.sessionDataManager.delete()).thenResolve(true)
        whenever(userComponent.relayClock.clockDiffUpdates).thenReturn(clockDiffUpdates)
        whenever(userComponent.accountLocalInfoManager.update(any())).thenResolveUnit()

        //used in finalizeInitialization

        //other
        whenever(userComponent.relayClientManager.state).thenReturn(RelayClientState.DISCONNECTED)
    }

    fun networkIsAvailable(isAvailable: Boolean) {
        appComponent.networkStatusSubject.onNext(isAvailable)
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

    @Test
    fun `it should create new installation data during initialization if no data exists`() {
        whenever(appComponent.installationDataPersistenceManager.store(any())).thenResolveUnit()
        whenever(appComponent.installationDataPersistenceManager.retrieve()).thenResolve(null)

        val app = createApp()
        app.init(appComponent)

        verify(appComponent.installationDataPersistenceManager).store(app.installationData)
    }

    @Test
    fun `it should create new installation data during initialization if data is corrupted`() {
        whenever(appComponent.installationDataPersistenceManager.retrieve()).thenReject(IOException("corrupt"))
        whenever(appComponent.installationDataPersistenceManager.store(any())).thenResolveUnit()

        val app = createApp()
        app.init(appComponent)

        assertTrue(app.isInitialized, "App didn't complete initialization")

        //accessing this will throw if it's unset
        verify(appComponent.installationDataPersistenceManager).store(app.installationData)
    }

    @Test
    fun `it should use existing installation data during initialization if data is present`() {
        val installationData = InstallationData.generate()
        whenever(appComponent.installationDataPersistenceManager.retrieve()).thenResolve(installationData)

        val app = createApp()
        app.init(appComponent)

        assertEquals(installationData, app.installationData, "Installation data doesn't match")
    }

    @Test
    fun `it should set isFirstRun to true if no installation data is present`() {
        whenever(appComponent.installationDataPersistenceManager.retrieve()).thenResolve(null)
        whenever(appComponent.installationDataPersistenceManager.store(any())).thenResolveUnit()

        val app = createApp()
        app.init(appComponent)

        assertTrue(app.isFirstRun)

        verify(appComponent.uiClientInfoService).isFirstRun = true
    }

    @Test
    fun `it should set isFirstRun to false if installation data is present`() {
        whenever(appComponent.installationDataPersistenceManager.retrieve()).thenResolve(InstallationData.generate())

        val app = createApp()
        app.init(appComponent)

        assertFalse(app.isFirstRun)

        verify(appComponent.uiClientInfoService).isFirstRun = false
    }

    @Ignore
    @Test
    fun `it should attempt to login automatically after basic initialization`() { TODO() }

    fun auth(authResult: AuthResult): SlyApplication {
        whenever(appComponent.authenticationService.auth(any(), any(), any())).thenResolve(authResult)
        val app = createApp()

        app.init(appComponent)

        doLogin(app)

        return app
    }

    fun authWithOtherDevices(otherDevices: List<DeviceInfo>?): SlyApplication {
        val authResult = AuthResult(SessionData(), MockUserComponent.keyVault, remotePasswordHash, accountInfo, accountLocalInfo, otherDevices)

        return auth(authResult)
    }

    fun authWithSessionData(sessionData: SessionData): SlyApplication {
        val authResult = AuthResult(sessionData, MockUserComponent.keyVault, remotePasswordHash, accountInfo, accountLocalInfo, null)

        return auth(authResult)
    }

    fun auth(): SlyApplication {
        val authResult = AuthResult(SessionData(), MockUserComponent.keyVault, remotePasswordHash, accountInfo, accountLocalInfo, null)

        return auth(authResult)
    }

    @Test
    fun `MessageCipherService should be initialized before use`() {
        val otherDevices = listOf(DeviceInfo(randomDeviceId(), randomRegistrationId()))
        authWithOtherDevices(otherDevices)

        val messageCipherService = appComponent.userComponent.messageCipherService
        val order = inOrder(messageCipherService)

        order.verify(messageCipherService).init()
        order.verify(messageCipherService).updateSelfDevices(any())
    }

    @Test
    fun `our own account must be added to the address book before MessageCipherService is called`() {
        val otherDevices = listOf(DeviceInfo(randomDeviceId(), randomRegistrationId()))
        authWithOtherDevices(otherDevices)

        val messageCipherService = appComponent.userComponent.messageCipherService
        val contactsService = appComponent.userComponent.contactsService

        val order = inOrder(contactsService, messageCipherService)

        order.verify(contactsService).addSelf(any())
        order.verify(messageCipherService).updateSelfDevices(any())
    }

    @Test
    fun `it should not add our own account after initial initialization`() {
        authWithOtherDevices(null)

        val contactsService = appComponent.userComponent.contactsService

        verify(contactsService, never()).addSelf(any())
    }

    @Test
    fun `it should update the self devices list during user initialization`() {
        val otherDevices = listOf(DeviceInfo(randomDeviceId(), randomRegistrationId()))
        authWithOtherDevices(otherDevices)
        verify(appComponent.userComponent.messageCipherService).updateSelfDevices(otherDevices)
    }

    @Test
    fun `it should send other devices a new device message during first initialization`() {
        val otherDevices = listOf(DeviceInfo(randomDeviceId(), randomRegistrationId()))
        val app = authWithOtherDevices(otherDevices)

        val deviceInfo = DeviceInfo(accountInfo.deviceId, app.installationData.registrationId)

        verify(appComponent.userComponent.messengerService).broadcastNewDevice(deviceInfo)
    }

    @Test
    fun `it should update the session data auth token when receiving an auth token event`() {
        val app = authWithOtherDevices(null)

        val authToken = randomAuthToken()
        userComponent.mockAuthTokenManager.newTokenSubject.onNext(authToken)

        verify(userComponent.sessionDataManager).updateAuthToken(authToken)
    }

    @Test
    fun `it should delete session data on log out`() {
        val app = authWithOtherDevices(null)

        app.logout()

        verify(userComponent.sessionDataManager).delete()
    }

    @Test
    fun `it should update the relay clock diff when RelayClock changes`() {
        val app = authWithOtherDevices(null)

        val diff = 4000L

        clockDiffUpdates.onNext(diff)

        verify(userComponent.sessionDataManager).updateClockDifference(diff)
    }

    @Test
    fun `it should initialize RelayClock with the clock diff value from SessionData during login`() {
        val sessionData = SessionData().copy(relayClockDifference = 4000L)

        val app = authWithSessionData(sessionData)

        verify(userComponent.relayClock).setDifference(sessionData.relayClockDifference)
    }

    fun testNoRelayConnection(relayClientState: RelayClientState) {
        val app = auth()

        app.isInBackground = false

        whenever(userComponent.relayClientManager.state).thenReturn(relayClientState)

        appComponent.networkStatusSubject.onNext(true)

        try {
            verify(userComponent.relayClientManager, never()).connect(any())
        }
        catch (e: NeverWantedButInvoked) {
            throw AssertionError("connect() called for state=$relayClientState")
        }
    }

    @Test
    fun `it should not attempt to reconnect when the relay client state is not disconnected`() {
        val states = listOf(
            RelayClientState.AUTHENTICATING,
            RelayClientState.AUTHENTICATED,
            RelayClientState.CONNECTED,
            RelayClientState.CONNECTING,
            RelayClientState.DISCONNECTING
        )

        states.forEach { testNoRelayConnection(it) }
    }

    @Test
    fun `it should connect to the relay when the client state is disconnected`() {
        val app = auth()

        app.isInBackground = false

        whenever(userComponent.relayClientManager.state).thenReturn(RelayClientState.DISCONNECTED)

        appComponent.networkStatusSubject.onNext(true)

        verify(userComponent.relayClientManager).connect(any())
    }

    @Ignore
    @Test
    fun `it should not connect to the relay while in the background if network is available`() { TODO() }

    @Ignore
    @Test
    fun `it should reconnect to the relay when brought to the foreground and network is available`() { TODO() }

    @Ignore
    @Test
    fun `it should reconnect if connect was called while disconnecting`() { TODO() }

    @Test
    fun `it must call for a version check on startup`() {
        val app = createApp()

        app.init(appComponent)

        verify(appComponent.versionChecker).init()
    }

    @Test
    fun `it should check prekeys after login initialization`() {
        val app = auth()

        verify(userComponent.preKeyManager).checkForUpload()
    }

    @Test
    fun `it should ignore app config file read failures and continue initialization`() {
        whenever(appComponent.appConfigBackend.read<AppConfig>(any())).thenReject(TestException())

        val app = createApp()

        app.init(appComponent)

        assertTrue(app.isInitialized, "App must finish initializing")
    }

    //FIXME need to refactor to make this possible
    @Ignore
    @Test
    fun `it should notify the bug submitter of the initial network status`() {
        TODO()
    }
}
