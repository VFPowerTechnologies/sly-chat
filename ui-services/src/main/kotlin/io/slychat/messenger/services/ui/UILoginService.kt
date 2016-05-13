package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.services.LoginEvent

/** Responsible for authentication */
@JSToJavaGenerate("LoginService")
interface UILoginService {
    fun addLoginEventListener(listener: (LoginEvent) -> Unit)
    fun login(emailOrPhoneNumber: String, password: String, rememberMe: Boolean)
    fun logout()
}