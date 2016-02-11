package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/**
 * Responsible for new account registration
 */
@JSToJavaGenerate
interface RegistrationService {
    /** Register a new account with the given info */
    fun doRegistration(info: RegistrationInfo): Promise<RegistrationResult, Exception>

    fun addListener(listener: (String) -> Unit)
}