package io.slychat.messenger.services.ui.dummy

import io.slychat.messenger.services.ui.UINetworkStatus
import io.slychat.messenger.services.ui.UINetworkStatusService
import io.slychat.messenger.services.ui.UIRelayStatus

class DummyUINetworkStatusService : UINetworkStatusService {
    override fun addNetworkStatusChangeListener(listener: (UINetworkStatus) -> Unit) {
        listener(UINetworkStatus(true, false))
    }

    override fun addRelayStatusChangeListener(listener: (UIRelayStatus) -> Unit) {
        listener(UIRelayStatus(true, "", "127.0.0.1"))
    }
}