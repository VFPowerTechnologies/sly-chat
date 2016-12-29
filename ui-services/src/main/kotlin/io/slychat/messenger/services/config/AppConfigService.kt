package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.SlyAddressKeyDeserializer
import io.slychat.messenger.core.SlyAddressKeySerializer
import io.slychat.messenger.services.DeviceTokens
import java.util.*

/**
 * @property formatVersion Configuration version.
 * @property loginRememberMe Last state of "Remember me" on login screen.
 * @property appearanceTheme Current theme.
 * @property pushNotificationsPermRequested Whether or not permissions to send push notifications has been requested yet.
 * @property pushNotificationsTokens Current push notification device tokens.
 * @property pushNotificationsRegistrations Current list of registered accounts and their unregistration tokens.
 * @property pushNotificationsUnregistrations Current list of accounts to be unregistered, and their unregistration tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppConfig(
    @JsonProperty("formatVersion")
    val formatVersion: Int = 1,

    @JsonProperty("loginRememberMe")
    val loginRememberMe: Boolean = true,

    @JsonProperty("appearenceTheme")
    val appearanceTheme: String? = null,

    @JsonProperty("pushNotificationsPermRequested")
    val pushNotificationsPermRequested: Boolean = false,

    @JsonProperty("pushNotificationsTokens")
    val pushNotificationsTokens: DeviceTokens? = null,

    @JsonDeserialize(keyUsing = SlyAddressKeyDeserializer::class)
    @JsonSerialize(keyUsing = SlyAddressKeySerializer::class)
    @JsonProperty("pushNotificationsRegistrations")
    val pushNotificationsRegistrations: Map<SlyAddress, String> = emptyMap(),

    @JsonDeserialize(keyUsing = SlyAddressKeyDeserializer::class)
    @JsonSerialize(keyUsing = SlyAddressKeySerializer::class)
    @JsonProperty("pushNotificationsUnregistrations")
    val pushNotificationsUnregistrations: Map<SlyAddress, String> = emptyMap()
) {
    companion object {
        private fun join(parent: String, child: String): String = "$parent.$child"

        val LOGIN_REMEMBER_ME = "app.login.rememberMe"

        val APPEARANCE = "app.appearance"
        val APPEARANCE_THEME = join(APPEARANCE, "theme")

        val PUSH_NOTIFICATIONS = "app.pushNotifications"
        val PUSH_NOTIFICATIONS_PERM_REQUESTED = join(PUSH_NOTIFICATIONS, "permRequested")
        val PUSH_NOTIFICATIONS_TOKENS = join(PUSH_NOTIFICATIONS, "tokens")
        val PUSH_NOTIFICATIONS_REGISTRATIONS = join(PUSH_NOTIFICATIONS, "registrations")
        val PUSH_NOTIFICATIONS_UNREGISTRATIONS = join(PUSH_NOTIFICATIONS, "unregistrations")
    }
}

class AppEditorInterface(override var config: AppConfig) : ConfigServiceBase.EditorInterface<AppConfig> {
    override val modifiedKeys = HashSet<String>()

    var loginRememberMe: Boolean
        get() = config.loginRememberMe
        set(value) {
            if (value != loginRememberMe) {
                modifiedKeys.add(AppConfig.LOGIN_REMEMBER_ME)
                config = config.copy(loginRememberMe = value)
            }
        }

    var appearanceTheme: String?
        get() = config.appearanceTheme
        set(value) {
            if (value != appearanceTheme) {
                modifiedKeys.add(AppConfig.APPEARANCE_THEME)
                config = config.copy(appearanceTheme = value)
            }
        }

    var pushNotificationsPermRequested: Boolean
        get() = config.pushNotificationsPermRequested
        set(value) {
            if (value != pushNotificationsPermRequested) {
                modifiedKeys.add(AppConfig.PUSH_NOTIFICATIONS_PERM_REQUESTED)
                config = config.copy(pushNotificationsPermRequested = value)
            }
        }

    var pushNotificationsTokens: DeviceTokens?
        get() = config.pushNotificationsTokens
        set(value) {
            if (value != pushNotificationsTokens) {
                modifiedKeys.add(AppConfig.PUSH_NOTIFICATIONS_TOKENS)
                config = config.copy(pushNotificationsTokens = value)
            }
        }

    var pushNotificationsRegistrations: Map<SlyAddress, String>
        get() = config.pushNotificationsRegistrations
        set(value) {
            if (value != pushNotificationsRegistrations) {
                modifiedKeys.add(AppConfig.PUSH_NOTIFICATIONS_REGISTRATIONS)
                config = config.copy(pushNotificationsRegistrations = value)
            }
        }

    var pushNotificationsUnregistrations: Map<SlyAddress, String>
        get() = config.pushNotificationsUnregistrations
        set(value) {
            if (value != pushNotificationsUnregistrations) {
                modifiedKeys.add(AppConfig.PUSH_NOTIFICATIONS_UNREGISTRATIONS)
                config = config.copy(pushNotificationsUnregistrations = value)
            }
        }
}

class AppConfigService(
    backend: ConfigBackend,
    override var config: AppConfig = AppConfig()
) : ConfigServiceBase<AppConfig, AppEditorInterface>(backend) {
    override fun makeEditor(): AppEditorInterface = AppEditorInterface(config)

    override val configClass: Class<AppConfig> = AppConfig::class.java

    val loginRememberMe: Boolean
        get() = config.loginRememberMe

    val appearanceTheme: String?
        get() = config.appearanceTheme

    val pushNotificationsPermRequested: Boolean
        get() = config.pushNotificationsPermRequested

    val pushNotificationsTokens: DeviceTokens?
        get() = config.pushNotificationsTokens

    val pushNotificationsRegistrations: Map<SlyAddress, String>
        get() = config.pushNotificationsRegistrations

    val pushNotificationsUnregistrations: Map<SlyAddress, String>
        get() = config.pushNotificationsUnregistrations
}
