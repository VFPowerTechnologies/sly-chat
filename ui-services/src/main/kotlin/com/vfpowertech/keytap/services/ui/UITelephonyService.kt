package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("TelephonyService")
interface UITelephonyService {
    fun getDevicePhoneNumber(): Promise<String?, Exception>
}