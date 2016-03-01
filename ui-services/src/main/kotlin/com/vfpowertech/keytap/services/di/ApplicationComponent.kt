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

    val platformInfoService: PlatformInfoService

    val registrationService: RegistrationService

    val loginService: LoginService

    val contactsService: ContactsService

    val messengerService: MessengerService

    val historyService: HistoryService

    val develService: DevelService

    fun plus(userModule: UserModule): UserComponent
}