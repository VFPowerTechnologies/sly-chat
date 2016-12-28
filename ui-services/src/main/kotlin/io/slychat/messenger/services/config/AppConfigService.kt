package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.slychat.messenger.core.SlyAddress
import java.util.*

/**
 * @property formatVersion Configuration version.
 * @property loginRememberMe Last state of "Remember me" on login screen.
 * @property appearanceTheme Current theme.
 * @property pushNotificationRegistrations Current list of registered accounts and their unregistration tokens.
 * @property pushNotificationUnregistrations Current list of accounts to be unregistered, and their unregistration tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppConfig(
    val formatVersion: Int = 1,
    val loginRememberMe: Boolean = true,
    val appearanceTheme: String? = null,
    val pushNotificationToken: String? = null,
    val pushNotificationRegistrations: Map<SlyAddress, String> = emptyMap(),
    val pushNotificationUnregistrations: Map<SlyAddress, String> = emptyMap()
) {
    companion object {
        private fun join(parent: String, child: String): String = "$parent.$child"

        val LOGIN_REMEMBER_ME = "app.login.rememberMe"

        val APPEARANCE = "app.appearance"
        val APPEARANCE_THEME = join(APPEARANCE, "theme")

        val PUSH_NOTIFICATIONS = "app.pushNotifications"
        val PUSH_NOTIFICATIONS_TOKEN = join(PUSH_NOTIFICATIONS, "token")
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

    var pushNotificationsToken: String?
        get() = config.pushNotificationToken
        set(value) {
            if (value != pushNotificationsToken) {
                modifiedKeys.add(AppConfig.PUSH_NOTIFICATIONS_TOKEN)
                config = config.copy(pushNotificationToken = value)
            }
        }

    var pushNotificationsRegistrations: Map<SlyAddress, String>
        get() = config.pushNotificationRegistrations
        set(value) {
            if (value != pushNotificationsRegistrations) {
                modifiedKeys.add(AppConfig.PUSH_NOTIFICATIONS_REGISTRATIONS)
                config = config.copy(pushNotificationRegistrations = value)
            }
        }

    var pushNotificationsUnregistrations: Map<SlyAddress, String>
        get() = config.pushNotificationUnregistrations
        set(value) {
            if (value != pushNotificationsUnregistrations) {
                modifiedKeys.add(AppConfig.PUSH_NOTIFICATIONS_UNREGISTRATIONS)
                config = config.copy(pushNotificationUnregistrations = value)
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

    val pushNotificationsToken: String?
        get() = config.pushNotificationToken

    val pushNotificationsRegistrations: Map<SlyAddress, String>
        get() = config.pushNotificationRegistrations

    val pushNotificationsUnregistrations: Map<SlyAddress, String>
        get() = config.pushNotificationUnregistrations
}
