package com.vfpowertech.keytap.services.di

import com.vfpowertech.keytap.services.NetworkStatusService
import com.vfpowertech.keytap.services.ui.*
import dagger.Component
import javax.inject.Singleton

/** Composed of objects which must live for the lifetime of the application. */
@Singleton
@Component(modules = arrayOf(ApplicationModule::class, RelayModule::class, UIServicesModule::class, PlatformModule::class, PersistenceCoreModule::class))
interface ApplicationComponent {
    val networkStatusService: NetworkStatusService

    val platformInfoService: UIPlatformInfoService

    val registrationService: UIRegistrationService

    val loginService: UILoginService

    val contactsService: UIContactsService

    val messengerService: UIMessengerService

    val historyService: UIHistoryService

    val develService: UIDevelService

    fun plus(userModule: UserModule): UserComponent
}