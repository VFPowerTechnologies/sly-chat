package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

data class UINotificationConfig(
    @JsonProperty("enabled")
    val enabled: Boolean,
    @JsonProperty("sound")
    val sound: String?
)

@JSToJavaGenerate("ConfigService")
interface UIConfigService {
    fun getLoginRememberMe(): Boolean
    fun setLoginRememberMe(v: Boolean)

    fun getLastMessageTtl(): Long
    fun setLastMessageTtl(v: Long)

    fun setNotificationConfig(config: UINotificationConfig)

    fun addNotificationConfigChangeListener(listener: (UINotificationConfig) -> Unit)

    @Exclude
    fun clearListeners()
}