package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/**
 * Responsible for account modification
 */
@JSToJavaGenerate("AccountModificationService")
interface UIAccountModificationService {
    /**
     * Update the user account name with the given info.
     */
    fun updateName(name: String): Promise<UIAccountUpdateResult, Exception>

    /**
     * Request a phoneNumber update, phone is not changed before it has been verified.
     */
    fun requestPhoneUpdate(phoneNumber: String): Promise<UIAccountUpdateResult, Exception>

    /**
     * Confirm and update the requested phone number.
     */
    fun confirmPhoneNumber(smsCode: String): Promise<UIAccountUpdateResult, Exception>

    /**
     * Update the user email with the given one.
     */
    fun updateEmail(email: String): Promise<UIAccountUpdateResult, Exception>
}