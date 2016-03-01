package com.vfpowertech.keytap.services.ui

/**
 * On registering, each listener will be called with the current value.
 */
interface UINetworkStatusService {
    fun addNetworkStatusChangeListener(listener: (UINetworkStatus) -> Unit)
    fun addRelayStatusChangeListener(listener: (UIRelayStatus) -> Unit)
}
