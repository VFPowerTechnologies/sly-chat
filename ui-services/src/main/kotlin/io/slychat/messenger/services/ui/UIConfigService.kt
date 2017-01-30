package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.config.ConvoTTLSettings

data class UINotificationConfig(
    @JsonProperty("enabled")
    val enabled: Boolean,
    @JsonProperty("sound")
    val sound: String?,
    //this is ignored by the backend completely; just here for UI
    //is always set if sound is non-null when sending to ui, and may be null when receiving from the ui
    @JsonProperty("soundName")
    val soundName: String?
)

data class UIAppearanceConfig(
    @JsonProperty("theme")
    val theme: String?
)

data class UIMarketingConfig(
    @JsonProperty("showInviteFriends")
    val showInviteFriends: Boolean
)

@JSToJavaGenerate("ConfigService")
interface UIConfigService {
    fun getLoginRememberMe(): Boolean

    fun setLoginRememberMe(v: Boolean)

    fun getConvoTTLSettings(conversationId: ConversationId): ConvoTTLSettings?

    fun setConvoTTLSettings(conversationId: ConversationId, convoTTLSettings: ConvoTTLSettings)

    fun setNotificationConfig(config: UINotificationConfig)

    fun addNotificationConfigChangeListener(listener: (UINotificationConfig) -> Unit)

    fun setAppearanceConfig(config: UIAppearanceConfig)

    fun addAppearanceConfigChangeListener(listener: (UIAppearanceConfig) -> Unit)

    fun setMarketingConfig(config: UIMarketingConfig)

    fun addMarketingConfigChangeListener(listener: (UIMarketingConfig) -> Unit)

    @Exclude
    fun clearListeners()
}