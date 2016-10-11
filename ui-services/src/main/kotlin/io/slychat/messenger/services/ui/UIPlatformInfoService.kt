package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("PlatformInfoService")
interface UIPlatformInfoService {
    fun getInfo(): UIPlatformInfo
}