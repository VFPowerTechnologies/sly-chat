package com.vfpowertech.keytap.ui.services.js

import com.vfpowertech.jsbridge.processor.annotations.JSServiceName
import com.vfpowertech.jsbridge.processor.annotations.JavaToJSGenerate
import nl.komponents.kovenant.Promise

/** Native OS navigation support */
@JavaToJSGenerate
@JSServiceName("navigationService")
interface NavigationService {
    /** Perform a back action in the UI */
    fun doBack(): Promise<Unit, Exception>
}