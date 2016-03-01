package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.ui.UINetworkStatus
import com.vfpowertech.keytap.services.ui.UINetworkStatusService
import com.vfpowertech.keytap.services.ui.UIRelayStatus
import java.util.*

class UINetworkStatusServiceImpl(private val application: KeyTapApplication) : UINetworkStatusService {
    private val networkStatusListeners = ArrayList<(UINetworkStatus) -> Unit>()
    private val relayStatusListeners = ArrayList<(UIRelayStatus) -> Unit>()

    //cached values
    private var isNetworkAvailable = false
    private var isRelayAvailable = false

    init {
        application.networkAvailable.subscribe { updateNetworkStatus(it) }
        application.relayAvailable.subscribe { updateRelayStatus(it) }
    }

    private fun getCurrentNetworkStatus(): UINetworkStatus =
        UINetworkStatus(isNetworkAvailable, false)

    private fun getCurrentRelayStatus(): UIRelayStatus =
        UIRelayStatus(isRelayAvailable, null, "")

    private fun updateRelayStatus(v: Boolean) {
        isRelayAvailable = v
        for (listener in relayStatusListeners)
            listener(getCurrentRelayStatus())
    }

    private fun updateNetworkStatus(v: Boolean) {
        isNetworkAvailable = v
        for (listener in networkStatusListeners)
            listener(getCurrentNetworkStatus())
    }

    override fun addNetworkStatusChangeListener(listener: (UINetworkStatus) -> Unit) {
        networkStatusListeners.add(listener)
        listener(getCurrentNetworkStatus())
    }

    override fun addRelayStatusChangeListener(listener: (UIRelayStatus) -> Unit) {
        relayStatusListeners.add(listener)
        listener(getCurrentRelayStatus())
    }
}