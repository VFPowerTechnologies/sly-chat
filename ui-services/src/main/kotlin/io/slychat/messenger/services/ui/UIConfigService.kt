package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

data class UIAppConfig(
    val loginRememberMe: Boolean
)

@JSToJavaGenerate("ConfigService")
interface UIConfigService {
    fun getAppConfig(): UIAppConfig
}