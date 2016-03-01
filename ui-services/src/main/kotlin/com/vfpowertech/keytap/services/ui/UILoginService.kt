package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Responsible for authentication */
@JSToJavaGenerate
interface UILoginService {
    fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception>
    fun logout()
}