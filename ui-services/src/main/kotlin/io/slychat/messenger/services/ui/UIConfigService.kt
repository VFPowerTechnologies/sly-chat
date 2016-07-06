package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("ConfigService")
interface UIConfigService {
    fun getLoginRememberMe(): Boolean
    fun setLoginRememberMe(v: Boolean)
}