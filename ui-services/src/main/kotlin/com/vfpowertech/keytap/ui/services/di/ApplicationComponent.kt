package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.DevelService
import com.vfpowertech.keytap.ui.services.HistoryService
import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.MessengerService
import com.vfpowertech.keytap.ui.services.NetworkStatusService
import com.vfpowertech.keytap.ui.services.PlatformInfoService
import com.vfpowertech.keytap.ui.services.RegistrationService
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

    val dispatcher: Dispatcher

    fun plus(userModule: UserModule): UserComponent
}