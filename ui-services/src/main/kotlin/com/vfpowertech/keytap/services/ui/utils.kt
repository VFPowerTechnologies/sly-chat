@file:JvmName("UIServiceUtils")
package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.services.di.ApplicationComponent
import com.vfpowertech.keytap.services.ui.jstojava.*

/** Create required directory structure. */
fun createAppDirectories(platformInfo: PlatformInfo) {
    platformInfo.appFileStorageDirectory.mkdirs()
    platformInfo.dataFileStorageDirectory.mkdirs()
}

/** Registers all available UI services to the given Dispatcher. */
fun registerCoreServicesOnDispatcher(dispatcher: Dispatcher, applicationComponent: ApplicationComponent) {
    val registrationService = applicationComponent.registrationService
    dispatcher.registerService("RegistrationService", RegistrationServiceToJavaProxy(registrationService, dispatcher))

    val platformInfoService = applicationComponent.platformInfoService
    dispatcher.registerService("PlatformInfoService", PlatformInfoServiceToJavaProxy(platformInfoService, dispatcher))

    val loginService = applicationComponent.loginService
    dispatcher.registerService("LoginService", LoginServiceToJavaProxy(loginService, dispatcher))

    val contactsService = applicationComponent.contactsService
    dispatcher.registerService("ContactsService", ContactsServiceToJavaProxy(contactsService, dispatcher))

    val messengerService = applicationComponent.messengerService
    dispatcher.registerService("MessengerService", MessengerServiceToJavaProxy(messengerService, dispatcher))

    val historyService = applicationComponent.historyService
    dispatcher.registerService("HistoryService", HistoryServiceToJavaProxy(historyService, dispatcher))

    val develService = applicationComponent.develService
    dispatcher.registerService("DevelService", DevelServiceToJavaProxy(develService, dispatcher))
}
