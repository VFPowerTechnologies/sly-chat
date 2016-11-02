package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class AppConfig(
    val formatVersion: Int = 1,
    val loginRememberMe: Boolean = true,
    val appearanceTheme: String? = null
) {
    companion object {
        val LOGIN_REMEMBER_ME = "app.login.rememberMe"

        val APPEARANCE = "app.appearance"

        val APPEARANCE_THEME = "app.appearance.theme"
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
}
