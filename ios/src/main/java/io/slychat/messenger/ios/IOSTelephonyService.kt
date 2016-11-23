package io.slychat.messenger.ios

import io.slychat.messenger.services.PlatformTelephonyService
import nl.komponents.kovenant.Promise

class IOSTelephonyService : PlatformTelephonyService {
    override fun getDevicePhoneNumber(): Promise<String?, Exception> {
        return Promise.ofSuccess(null)
    }
}