package io.slychat.messenger.desktop

import io.slychat.messenger.services.PlatformTelephonyService
import nl.komponents.kovenant.Promise

class DesktopTelephonyService : PlatformTelephonyService {
    override fun getDevicePhoneNumber(): Promise<String?, Exception> = Promise.ofSuccess(null)
}
