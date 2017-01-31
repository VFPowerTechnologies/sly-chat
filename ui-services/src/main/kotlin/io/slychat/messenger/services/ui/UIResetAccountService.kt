package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("ResetAccountService")
interface UIResetAccountService {
    /** Initialize the account reset process */
    fun resetAccount(username: String): Promise<UIRequestResetAccountResult, Exception>

    /** Submit the email confirmation code to the web server */
    fun submitEmailConfirmationCode(username: String, code: String): Promise<UIResetAccountResult, Exception>

    /** Submit the phone number confirmation code to the web server */
    fun submitPhoneNumberConfirmationCode(username: String, code: String): Promise<UIResetAccountResult, Exception>

}