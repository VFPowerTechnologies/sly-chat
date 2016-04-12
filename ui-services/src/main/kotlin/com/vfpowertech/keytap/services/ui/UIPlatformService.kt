package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("PlatformService")
interface UIPlatformService {
    fun openURL(url: String)
}