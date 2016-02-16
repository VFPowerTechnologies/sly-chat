@file:JvmName("UIServiceUtils")
package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import com.vfpowertech.keytap.ui.services.di.UIServicesComponent
import com.vfpowertech.keytap.ui.services.jstojava.RegistrationServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.PlatformInfoServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.MessengerServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.LoginServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.ContactsServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.HistoryServiceToJavaProxy
import com.vfpowertech.keytap.ui.services.jstojava.DevelServiceToJavaProxy

/** Registers all available UI services to the given Dispatcher. */
fun registerServicesOnDispatcher(dispatcher: Dispatcher, uiServicesComponent: UIServicesComponent) {
    val registrationService = uiServicesComponent.registrationService
    dispatcher.registerService("RegistrationService", RegistrationServiceToJavaProxy(registrationService,  dispatcher))

    val platformInfoService = uiServicesComponent.platformInfoService
    dispatcher.registerService("PlatformInfoService", PlatformInfoServiceToJavaProxy(platformInfoService, dispatcher))

    val loginService = uiServicesComponent.loginService
    dispatcher.registerService("LoginService", LoginServiceToJavaProxy(loginService, dispatcher))

    val contactsService = uiServicesComponent.contactsService
    dispatcher.registerService("ContactsService", ContactsServiceToJavaProxy(contactsService, dispatcher))

    val messengerService = uiServicesComponent.messengerService
    dispatcher.registerService("MessengerService", MessengerServiceToJavaProxy(messengerService, dispatcher))

    val historyService = uiServicesComponent.historyService
    dispatcher.registerService("HistoryService", HistoryServiceToJavaProxy(historyService, dispatcher))

    val develService = uiServicesComponent.develService
    dispatcher.registerService("DevelService", DevelServiceToJavaProxy(develService, dispatcher))

}