package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Responsible for authentication */
@JSToJavaGenerate
interface LoginService {
    fun login(emailOrPhoneNumber: String, password: String): Promise<UILoginResult, Exception>
}