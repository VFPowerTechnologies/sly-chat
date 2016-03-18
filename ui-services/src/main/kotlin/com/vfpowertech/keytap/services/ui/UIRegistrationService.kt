package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/**
 * Responsible for new account registration
 */
@JSToJavaGenerate("RegistrationService")
interface UIRegistrationService {
    /** Register a new account with the given info */
    fun doRegistration(info: UIRegistrationInfo): Promise<UIRegistrationResult, Exception>

    fun addListener(listener: (String) -> Unit)

    fun submitVerificationCode(username: String, code: String): Promise<UISmsVerificationStatus, Exception>

    fun resendVerificationCode(username: String): Promise<UISmsVerificationStatus, Exception>
}