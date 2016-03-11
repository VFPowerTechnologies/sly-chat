package com.vfpowertech.keytap.services.ui

import nl.komponents.kovenant.Promise

interface PlatformTelephonyService {
    /** Return the device's telephone number, if available. */
    fun getDevicePhoneNumber(): Promise<String?, Exception>
}