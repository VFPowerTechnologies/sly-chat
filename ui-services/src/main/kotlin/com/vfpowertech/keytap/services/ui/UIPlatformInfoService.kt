package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("PlatformInfoService")
interface UIPlatformInfoService {
    fun getInfo(): UIPlatformInfo
}