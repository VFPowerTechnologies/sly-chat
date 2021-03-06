package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.services.ui.impl.UILoginEvent
import nl.komponents.kovenant.Promise

/** Responsible for authentication */
@JSToJavaGenerate("LoginService")
interface UILoginService {
    fun areAccountsPresent(): Promise<Boolean, Exception>

    fun addLoginEventListener(listener: (UILoginEvent) -> Unit)

    fun login(emailOrPhoneNumber: String, password: String, rememberMe: Boolean)

    fun logout()

    @Exclude
    fun clearListeners()
}