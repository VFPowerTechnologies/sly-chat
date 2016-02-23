@file:JvmName("UIServiceUtils")
package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.core.PlatformInfo
import com.vfpowertech.keytap.ui.services.di.ApplicationComponent
import com.vfpowertech.keytap.ui.services.jstojava.ContactsServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.DevelServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.HistoryServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.LoginServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.MessengerServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.PlatformInfoServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.RegistrationServiceToJavaProxy

/** Create required directory structure. */
fun createAppDirectories(platformInfo: PlatformInfo) {
    platformInfo.appFileStorageDirectory.mkdirs()
    platformInfo.dataFileStorageDirectory.mkdirs()
}

/** Registers all available UI services to the given Dispatcher. */
fun registerCoreServicesOnDispatcher(dispatcher: Dispatcher, applicationComponent: ApplicationComponent) {
    val registrationService = applicationComponent.registrationService
    dispatcher.registerService("RegistrationService", RegistrationServiceToJavaProxy(registrationService,  dispatcher))

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
