package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class AppConfig(
    val formatVersion: Int = 1,
    val loginRememberMe: Boolean = true
) {
    companion object {
        val LOGIN_REMEMBER_ME = "app.login.rememberMe"
    }
}

class AppEditorInterface(override var config: AppConfig) : ConfigServiceBase.EditorInterface<AppConfig> {
    override val modifiedKeys = HashSet<String>()

    var loginRememberMe: Boolean
        get() = config.loginRememberMe
        set(value) {
            modifiedKeys.add(AppConfig.LOGIN_REMEMBER_ME)
            config = config.copy(loginRememberMe = value)
        }
}

class AppConfigService(
    backend: JsonConfigBackend,
    override var config: AppConfig = AppConfig()
) : ConfigServiceBase<AppConfig, AppEditorInterface>(backend) {
    override fun makeEditor(): AppEditorInterface = AppEditorInterface(config)

    override val configClass: Class<AppConfig> = AppConfig::class.java

    val loginRememberMe: Boolean
        get() = config.loginRememberMe
}
