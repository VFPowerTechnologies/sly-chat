package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import com.vfpowertech.keytap.services.LoginEvent
import nl.komponents.kovenant.Promise

/** Responsible for authentication */
@JSToJavaGenerate("InfoService")
interface UIInfoService {
    /** Get the user country based on his ip. */
    fun getGeoLocation(): Promise<String?, Exception>
}