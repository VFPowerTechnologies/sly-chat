package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.ui.UINetworkStatus
import com.vfpowertech.keytap.services.ui.UINetworkStatusService
import com.vfpowertech.keytap.services.ui.UIRelayStatus

class DummyUINetworkStatusService : UINetworkStatusService {
    override fun addNetworkStatusChangeListener(listener: (UINetworkStatus) -> Unit) {
        listener(UINetworkStatus(true, false))
    }

    override fun addRelayStatusChangeListener(listener: (UIRelayStatus) -> Unit) {
        listener(UIRelayStatus(true, "", "127.0.0.1"))
    }
}