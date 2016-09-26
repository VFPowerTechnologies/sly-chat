package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.services.config.SoundFilePath

data class UINotificationConfig(
    @JsonProperty("enabled")
    val enabled: Boolean,
    @JsonProperty("sound")
    val sound: SoundFilePath?
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