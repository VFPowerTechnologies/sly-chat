package com.vfpowertech.keytap.desktop

import com.vfpowertech.keytap.services.PlatformTelephonyService
import nl.komponents.kovenant.Promise

class DesktopTelephonyService : PlatformTelephonyService {
    override fun getDevicePhoneNumber(): Promise<String?, Exception> = Promise.ofSuccess(null)
}
