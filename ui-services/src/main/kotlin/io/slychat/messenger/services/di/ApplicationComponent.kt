package io.slychat.messenger.services.di

import dagger.Component
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.http.HttpClientFactory
import io.slychat.messenger.services.AuthenticationService
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.UserPathsGenerator
import io.slychat.messenger.services.ui.*
import rx.Scheduler
import javax.inject.Singleton

/** Composed of objects which must live for the lifetime of the application. */
@Singleton
@Component(modules = arrayOf(ApplicationModule::class, RelayModule::class, UIServicesModule::class, PlatformModule::class))
interface ApplicationComponent {
    val platformInfo: PlatformInfo

    val platformInfoService: UIPlatformInfoService

    val registrationService: UIRegistrationService

    val loginService: UILoginService

    val contactsService: UIContactsService

    val messengerService: UIMessengerService

    val historyService: UIHistoryService

    val develService: UIDevelService

    val uiNetworkStatusService: UINetworkStatusService

    val stateService: UIStateService

    val eventService: UIEventService

    val userPathsGenerator: UserPathsGenerator

    val rxScheduler: Scheduler

    val authenticationService: AuthenticationService

    val telephonyService: UITelephonyService

    val windowService: UIWindowService

    val loadService: UILoadService

    val infoService: UIInfoService

    var accountModificationService: UIAccountModificationService

    val platformService: UIPlatformService

    val platformContacts: PlatformContacts

    val serverUrls: BuildConfig.ServerUrls

    @get:SlyHttp
    val slyHttpClientFactory: HttpClientFactory

    @get:ExternalHttp
    val externalHttpClientFactory: HttpClientFactory

    fun plus(userModule: UserModule): UserComponent
}