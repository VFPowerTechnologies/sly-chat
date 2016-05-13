@file:JvmName("UIServiceUtils")
package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.ui.jstojava.*

/** Create required directory structure. */
fun createAppDirectories(platformInfo: PlatformInfo) {
    platformInfo.appFileStorageDirectory.mkdirs()
    platformInfo.dataFileStorageDirectory.mkdirs()
}

/** Registers all available UI services to the given Dispatcher. */
fun registerCoreServicesOnDispatcher(dispatcher: Dispatcher, applicationComponent: ApplicationComponent) {
    val registrationService = applicationComponent.registrationService
    dispatcher.registerService("RegistrationService", UIRegistrationServiceToJavaProxy(registrationService, dispatcher))

    val platformInfoService = applicationComponent.platformInfoService
    dispatcher.registerService("PlatformInfoService", UIPlatformInfoServiceToJavaProxy(platformInfoService, dispatcher))

    val loginService = applicationComponent.loginService
    dispatcher.registerService("LoginService", UILoginServiceToJavaProxy(loginService, dispatcher))

    val contactsService = applicationComponent.contactsService
    dispatcher.registerService("ContactsService", UIContactsServiceToJavaProxy(contactsService, dispatcher))

    val messengerService = applicationComponent.messengerService
    dispatcher.registerService("MessengerService", UIMessengerServiceToJavaProxy(messengerService, dispatcher))

    val historyService = applicationComponent.historyService
    dispatcher.registerService("HistoryService", UIHistoryServiceToJavaProxy(historyService, dispatcher))

    val develService = applicationComponent.develService
    dispatcher.registerService("DevelService", UIDevelServiceToJavaProxy(develService, dispatcher))

    val networkStatusService = applicationComponent.uiNetworkStatusService
    dispatcher.registerService("NetworkStatusService", UINetworkStatusServiceToJavaProxy(networkStatusService, dispatcher))

    val stateService = applicationComponent.stateService
    dispatcher.registerService("StateService", UIStateServiceToJavaProxy(stateService, dispatcher))

    val telephonyService = applicationComponent.telephonyService
    dispatcher.registerService("TelephonyService", UITelephonyServiceToJavaProxy(telephonyService, dispatcher))

    val windowService = applicationComponent.windowService
    dispatcher.registerService("WindowService", UIWindowServiceToJavaProxy(windowService, dispatcher))

    val eventService = applicationComponent.eventService
    dispatcher.registerService("EventService", UIEventServiceToJavaProxy(eventService, dispatcher))

    val accountModificationService = applicationComponent.accountModificationService
    dispatcher.registerService("AccountModificationService", UIAccountModificationServiceToJavaProxy(accountModificationService, dispatcher))

    val platformService = applicationComponent.platformService
    dispatcher.registerService("PlatformService", UIPlatformServiceToJavaProxy(platformService, dispatcher))

    val loadService = applicationComponent.loadService
    dispatcher.registerService("LoadService", UILoadServiceToJavaProxy(loadService, dispatcher))

    val infoService = applicationComponent.infoService
    dispatcher.registerService("InfoService", UIInfoServiceToJavaProxy(infoService, dispatcher))
}
