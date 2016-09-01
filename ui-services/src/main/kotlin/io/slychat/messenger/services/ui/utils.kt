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
    val registrationService = applicationComponent.uiRegistrationService
    dispatcher.registerService("RegistrationService", UIRegistrationServiceToJavaProxy(registrationService, dispatcher))

    val platformInfoService = applicationComponent.uiPlatformInfoService
    dispatcher.registerService("PlatformInfoService", UIPlatformInfoServiceToJavaProxy(platformInfoService, dispatcher))

    val loginService = applicationComponent.uiLoginService
    dispatcher.registerService("LoginService", UILoginServiceToJavaProxy(loginService, dispatcher))

    val contactsService = applicationComponent.uiContactsService
    dispatcher.registerService("ContactsService", UIContactsServiceToJavaProxy(contactsService, dispatcher))

    val messengerService = applicationComponent.uiMessengerService
    dispatcher.registerService("MessengerService", UIMessengerServiceToJavaProxy(messengerService, dispatcher))

    val historyService = applicationComponent.uiHistoryService
    dispatcher.registerService("HistoryService", UIHistoryServiceToJavaProxy(historyService, dispatcher))

    val develService = applicationComponent.uiDevelService
    dispatcher.registerService("DevelService", UIDevelServiceToJavaProxy(develService, dispatcher))

    val networkStatusService = applicationComponent.uiNetworkStatusService
    dispatcher.registerService("NetworkStatusService", UINetworkStatusServiceToJavaProxy(networkStatusService, dispatcher))

    val stateService = applicationComponent.uiStateService
    dispatcher.registerService("StateService", UIStateServiceToJavaProxy(stateService, dispatcher))

    val telephonyService = applicationComponent.uiTelephonyService
    dispatcher.registerService("TelephonyService", UITelephonyServiceToJavaProxy(telephonyService, dispatcher))

    val windowService = applicationComponent.uiWindowService
    dispatcher.registerService("WindowService", UIWindowServiceToJavaProxy(windowService, dispatcher))

    val eventService = applicationComponent.uiEventService
    dispatcher.registerService("EventService", UIEventServiceToJavaProxy(eventService, dispatcher))

    val accountModificationService = applicationComponent.uiAccountModificationService
    dispatcher.registerService("AccountModificationService", UIAccountModificationServiceToJavaProxy(accountModificationService, dispatcher))

    val platformService = applicationComponent.uiPlatformService
    dispatcher.registerService("PlatformService", UIPlatformServiceToJavaProxy(platformService, dispatcher))

    val loadService = applicationComponent.uiLoadService
    dispatcher.registerService("LoadService", UILoadServiceToJavaProxy(loadService, dispatcher))

    val infoService = applicationComponent.uiInfoService
    dispatcher.registerService("InfoService", UIInfoServiceToJavaProxy(infoService, dispatcher))

    val configService = applicationComponent.uiConfigService
    dispatcher.registerService("ConfigService", UIConfigServiceToJavaProxy(configService, dispatcher))

    val groupService = applicationComponent.uiGroupService
    dispatcher.registerService("GroupService", UIGroupServiceToJavaProxy(groupService, dispatcher))

    val clientInfoService = applicationComponent.uiClientInfoService
    dispatcher.registerService("ClientInfoService", UIClientInfoServiceToJavaProxy(clientInfoService, dispatcher))
}
