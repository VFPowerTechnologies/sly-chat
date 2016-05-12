package io.slychat.messenger.services.ui.js

import com.vfpowertech.jsbridge.processor.annotations.JSServiceName
import com.vfpowertech.jsbridge.processor.annotations.JavaToJSGenerate
import nl.komponents.kovenant.Promise

/** Native OS navigation support. */
@JavaToJSGenerate
@JSServiceName("navigationService")
interface NavigationService {
    /** Perform a back action in the UI. */
    fun goBack(): Promise<Unit, Exception>

    /** Go to the specified url. */
    fun goTo(url: String): Promise<Unit, Exception>
}