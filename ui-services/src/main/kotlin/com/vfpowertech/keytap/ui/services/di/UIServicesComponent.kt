package com.vfpowertech.keytap.ui.services.di

import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.core.persistence.sqlite.SQLitePersistenceManager
import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.DevelService
import com.vfpowertech.keytap.ui.services.HistoryService
import com.vfpowertech.keytap.ui.services.LoginService
import com.vfpowertech.keytap.ui.services.MessengerService
import com.vfpowertech.keytap.ui.services.PlatformInfoService
import com.vfpowertech.keytap.ui.services.RegistrationService
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(CoreModule::class, PlatformModule::class, PersistenceModule::class))
interface UIServicesComponent {
    val platformInfoService: PlatformInfoService

    val registrationService: RegistrationService

    val loginService: LoginService

    val contactsService: ContactsService

    val messengerService: MessengerService

    val historyService: HistoryService

    val develService: DevelService

    val sqlitePersistenceManager: SQLitePersistenceManager

    val dispatcher: Dispatcher
}