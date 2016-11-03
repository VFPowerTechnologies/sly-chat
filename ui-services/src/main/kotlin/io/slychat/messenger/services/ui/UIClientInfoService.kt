package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.services.VersionCheckResult

/** Queries installed client info. */
@JSToJavaGenerate("ClientInfoService")
interface UIClientInfoService {
    @set:Exclude
    var isFirstRun: Boolean

    fun addVersionOutdatedListener(listener: (VersionCheckResult) -> Unit)

    @Exclude
    fun clearListeners()
}