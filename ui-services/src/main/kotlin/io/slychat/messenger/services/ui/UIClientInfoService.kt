package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

/** Queries installed client info. */
@JSToJavaGenerate("ClientInfoService")
interface UIClientInfoService {
    fun addVersionOutdatedListener(listener: () -> Unit)

    @Exclude
    fun clearListeners()
}