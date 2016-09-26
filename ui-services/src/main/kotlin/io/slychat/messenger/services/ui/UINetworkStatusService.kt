package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

/**
 * On registering, each listener will be called with the current value.
 */
@JSToJavaGenerate("NetworkStatusService")
interface UINetworkStatusService {
    fun addNetworkStatusChangeListener(listener: (UINetworkStatus) -> Unit)

    fun addRelayStatusChangeListener(listener: (UIRelayStatus) -> Unit)

    @Exclude
    fun clearListeners()
}
