package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("InfoService")
interface UIInfoService {
    /** Get the user country based on his ip. */
    fun getGeoLocation(): Promise<String?, Exception>
}