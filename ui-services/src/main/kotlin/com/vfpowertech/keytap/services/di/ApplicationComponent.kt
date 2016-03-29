package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.services.AuthenticationService
import com.vfpowertech.keytap.services.PlatformContacts
import com.vfpowertech.keytap.services.UserPathsGenerator
import com.vfpowertech.keytap.services.ui.*
import dagger.Component
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

    val uiConfigService: UIConfigService

    val stateService: UIStateService

    val eventService: UIEventService

    val userPathsGenerator: UserPathsGenerator

    val rxScheduler: Scheduler

    val authenticationService: AuthenticationService

    val telephonyService: UITelephonyService

    val windowService: UIWindowService

    val platformContacts: PlatformContacts

    val serverUrls: BuildConfig.ServerUrls

    fun plus(userModule: UserModule): UserComponent
}