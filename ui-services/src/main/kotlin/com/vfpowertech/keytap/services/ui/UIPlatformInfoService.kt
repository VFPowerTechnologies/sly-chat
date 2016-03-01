package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate
interface UIPlatformInfoService {
    fun getInfo(): UIPlatformInfo
}