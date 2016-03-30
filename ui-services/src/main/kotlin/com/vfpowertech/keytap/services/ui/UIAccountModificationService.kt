package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/**
 * Responsible for account modification
 */
@JSToJavaGenerate("AccountModificationService")
interface UIAccountModificationService {
    /** Update phone with the given info */
    fun updatePhone(info: UiUpdatePhoneInfo): Promise<UIUpdatePhoneResult, Exception>
}