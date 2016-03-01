package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/**
 * Responsible for new account registration
 */
@JSToJavaGenerate
interface RegistrationService {
    /** Register a new account with the given info */
    fun doRegistration(info: UIRegistrationInfo): Promise<UIRegistrationResult, Exception>

    fun addListener(listener: (String) -> Unit)
}