package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.mock
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.persistence.InstallationDataPersistenceManager
import io.slychat.messenger.services.auth.AuthenticationService
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.di.UserModule
import io.slychat.messenger.services.ui.*
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject

class MockApplicationComponent : ApplicationComponent {
    override val platformInfo: PlatformInfo = mock()

    override val uiPlatformInfoService: UIPlatformInfoService = mock()

    override val uiRegistrationService: UIRegistrationService = mock()

    override val uiLoginService: UILoginService = mock()

    override val uiContactsService: UIContactsService = mock()

    override val uiMessengerService: UIMessengerService = mock()

    override val uiHistoryService: UIHistoryService = mock()

    override val uiDevelService: UIDevelService = mock()

    override val uiNetworkStatusService: UINetworkStatusService = mock()

    override val uiStateService: UIStateService = mock()

    override val uiEventService: UIEventService = mock()

    override val uiClientInfoService: UIClientInfoService = mock()

    override val rxScheduler: Scheduler = Schedulers.immediate()

    override val authenticationService: AuthenticationService = mock()

    override val uiTelephonyService: UITelephonyService = mock()

    override val uiWindowService: UIWindowService = mock()

    override val uiLoadService: UILoadService = mock()

    override val uiInfoService: UIInfoService = mock()

    override val uiAccountModificationService: UIAccountModificationService = mock()
    
    override val uiFeedbackService: UIFeedbackService = mock()

    override val uiPlatformService: UIPlatformService = mock()

    override val platformContacts: PlatformContacts = mock()

    override val serverUrls: BuildConfig.ServerUrls = mock()

    override val appConfigService: AppConfigService = AppConfigService(DummyConfigBackend())

    override val slyHttpClientFactory: HttpClientFactory = mock()

    override val uiConfigService: UIConfigService = mock()

    override val uiGroupService: UIGroupService = mock()

    override val localAccountDirectory: LocalAccountDirectory = mock()

    override val installationDataPersistenceManager: InstallationDataPersistenceManager = mock()

    val networkStatusSubject: BehaviorSubject<Boolean> = BehaviorSubject.create(false)

    override val networkStatus: Observable<Boolean>
        get() = networkStatusSubject

    override val versionChecker: VersionChecker = mock()

    val userComponent = MockUserComponent()

    override fun plus(userModule: UserModule): UserComponent {
        return userComponent
    }
}