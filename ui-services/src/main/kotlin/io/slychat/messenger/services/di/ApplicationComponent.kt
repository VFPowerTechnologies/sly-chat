package io.slychat.messenger.services.di

import dagger.Component
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.core.persistence.InstallationDataPersistenceManager
import io.slychat.messenger.core.sentry.ReportSubmitter
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthenticationService
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.di.annotations.NetworkStatus
import io.slychat.messenger.services.di.annotations.SlyHttp
import io.slychat.messenger.services.di.annotations.UIVisibility
import io.slychat.messenger.services.ui.*
import rx.Observable
import rx.Scheduler
import javax.inject.Singleton

/** Composed of objects which must live for the lifetime of the application. */
@Singleton
@Component(modules = arrayOf(ApplicationModule::class, RelayModule::class, UIServicesModule::class, PlatformModule::class))
interface ApplicationComponent {
    //FIXME used in Sentry.init
    val platformInfo: PlatformInfo

    val uiPlatformInfoService: UIPlatformInfoService

    val uiRegistrationService: UIRegistrationService

    val uiLoginService: UILoginService

    val uiResetAccountService: UIResetAccountService

    val uiContactsService: UIContactsService

    val uiMessengerService: UIMessengerService

    val uiHistoryService: UIHistoryService

    val uiNetworkStatusService: UINetworkStatusService

    val uiStateService: UIStateService

    val uiEventService: UIEventService

    val rxScheduler: Scheduler

    val authenticationService: AuthenticationService

    val uiTelephonyService: UITelephonyService

    val uiWindowService: UIWindowService

    val uiLoadService: UILoadService

    val uiInfoService: UIInfoService

    val uiAccountModificationService: UIAccountModificationService

    val uiPlatformService: UIPlatformService

    val uiClientInfoService: UIClientInfoService

    val uiFeedbackService: UIFeedbackService

    val uiEventLogService: UIEventLogService

    val platformContacts: PlatformContacts

    //FIXME only used for gcm client in AndroidApp
    val serverUrls: SlyBuildConfig.ServerUrls

    val appConfigService: AppConfigService

    //FIXME only used for gcm client in AndroidApp
    @get:SlyHttp
    val slyHttpClientFactory: HttpClientFactory

    val uiConfigService: UIConfigService

    val uiGroupService: UIGroupService

    val uiShareService: UIShareService

    val localAccountDirectory: LocalAccountDirectory

    val installationDataPersistenceManager: InstallationDataPersistenceManager

    @get:NetworkStatus
    val networkStatus: Observable<Boolean>

    @get:UIVisibility
    val uiVisibility: Observable<Boolean>

    val versionChecker: VersionChecker

    val registrationService: RegistrationService

    val pushNotificationsManager: PushNotificationsManager

    val tokenFetchService: TokenFetchService

    val reportSubmitter: ReportSubmitter<ByteArray>?

    fun plus(userModule: UserModule): UserComponent
}