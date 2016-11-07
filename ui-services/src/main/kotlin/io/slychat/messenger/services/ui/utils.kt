@file:JvmName("UIServiceUtils")
package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.core.dispatcher.Dispatcher
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.ui.jstojava.*

/** Create required directory structure. */
fun createAppDirectories(platformInfo: PlatformInfo) {
    platformInfo.appFileStorageDirectory.mkdirs()
}

/** Registers all available UI services to the given Dispatcher. */
fun registerCoreServicesOnDispatcher(dispatcher: Dispatcher, applicationComponent: ApplicationComponent) {
    applicationComponent.apply {
        dispatcher.registerService(UIRegistrationServiceToJavaProxy(uiRegistrationService, dispatcher))

        dispatcher.registerService(UIPlatformInfoServiceToJavaProxy(uiPlatformInfoService, dispatcher))

        dispatcher.registerService(UILoginServiceToJavaProxy(uiLoginService, dispatcher))

        dispatcher.registerService(UIContactsServiceToJavaProxy(uiContactsService, dispatcher))

        dispatcher.registerService(UIMessengerServiceToJavaProxy(uiMessengerService, dispatcher))

        dispatcher.registerService(UIHistoryServiceToJavaProxy(uiHistoryService, dispatcher))

        dispatcher.registerService(UINetworkStatusServiceToJavaProxy(uiNetworkStatusService, dispatcher))

        dispatcher.registerService(UIStateServiceToJavaProxy(uiStateService, dispatcher))

        dispatcher.registerService(UITelephonyServiceToJavaProxy(uiTelephonyService, dispatcher))

        dispatcher.registerService(UIWindowServiceToJavaProxy(uiWindowService, dispatcher))

        dispatcher.registerService(UIEventServiceToJavaProxy(uiEventService, dispatcher))

        dispatcher.registerService(UIAccountModificationServiceToJavaProxy(uiAccountModificationService, dispatcher))

        dispatcher.registerService(UIPlatformServiceToJavaProxy(uiPlatformService, dispatcher))

        dispatcher.registerService(UILoadServiceToJavaProxy(uiLoadService, dispatcher))

        dispatcher.registerService(UIInfoServiceToJavaProxy(uiInfoService, dispatcher))

        dispatcher.registerService(UIConfigServiceToJavaProxy(uiConfigService, dispatcher))

        dispatcher.registerService(UIGroupServiceToJavaProxy(uiGroupService, dispatcher))

        dispatcher.registerService(UIClientInfoServiceToJavaProxy(uiClientInfoService, dispatcher))

        dispatcher.registerService(UIFeedbackServiceToJavaProxy(uiFeedbackService, dispatcher))

        dispatcher.registerService(UIEventLogServiceToJavaProxy(uiEventLogService, dispatcher))

        dispatcher.registerService(UIShareServiceToJavaProxy(uiShareService, dispatcher))
    }
}

fun clearAllListenersOnDispatcher(applicationComponent: ApplicationComponent) {
    applicationComponent.uiClientInfoService.clearListeners()
    applicationComponent.uiConfigService.clearListeners()
    applicationComponent.uiContactsService.clearListeners()
    applicationComponent.uiGroupService.clearListeners()
    applicationComponent.uiLoginService.clearListeners()
    applicationComponent.uiMessengerService.clearListeners()
    applicationComponent.uiNetworkStatusService.clearListeners()
    applicationComponent.uiRegistrationService.clearListeners()
    applicationComponent.uiWindowService.clearListeners()
}
