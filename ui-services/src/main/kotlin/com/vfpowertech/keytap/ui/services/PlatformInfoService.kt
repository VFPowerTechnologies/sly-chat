package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate
interface PlatformInfoService {
    fun getInfo(): UIPlatformInfo
}