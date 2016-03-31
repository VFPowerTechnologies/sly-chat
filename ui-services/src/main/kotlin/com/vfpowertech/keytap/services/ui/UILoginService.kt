package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import com.vfpowertech.keytap.services.LoginEvent
import nl.komponents.kovenant.Promise

/** Responsible for authentication */
@JSToJavaGenerate("LoginService")
interface UILoginService {
    fun addLoginEventListener(listener: (LoginEvent) -> Unit)
    fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception>
    fun logout()
}