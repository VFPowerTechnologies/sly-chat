package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.services.ui.PlatformTelephonyService
import com.vfpowertech.keytap.services.ui.UITelephonyService
import nl.komponents.kovenant.Promise

class UITelephonyServiceImpl(private val platformTelephonyService: PlatformTelephonyService) : UITelephonyService {
    override fun getDevicePhoneNumber(): Promise<String?, Exception> = platformTelephonyService.getDevicePhoneNumber()
}