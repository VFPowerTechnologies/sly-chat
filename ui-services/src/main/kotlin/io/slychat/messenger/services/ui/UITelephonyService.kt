package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("TelephonyService")
interface UITelephonyService {
    fun getDevicePhoneNumber(): Promise<String?, Exception>

    fun supportsMakingCalls(): Boolean

    fun callContact(userId: UserId)
}