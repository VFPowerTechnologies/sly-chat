package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

//paths are strings because android uses android.net.Uri for representing files (not all of which are convertable to
//real paths, eg content://settings/system/ringtone)
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserConfig(
    val formatVersion: Int = 1,
    val notificationsEnabled: Boolean = true,
    val notificationsSound: String? = null,
    val profileAvatar: String? = null
) {
    companion object {
        private fun join(parent: String, child: String): String = "$parent.$child"

        val NOTIFICATIONS = "user.notifications"

        val NOTIFICATIONS_SOUND = join(NOTIFICATIONS, "sound")
        val NOTIFICATIONS_ENABLED = join(NOTIFICATIONS, "enabled")
    }
}

class UserEditorInterface(override var config: UserConfig) : ConfigServiceBase.EditorInterface<UserConfig> {
    override val modifiedKeys = HashSet<String>()

    var notificationsEnabled: Boolean
        get() = config.notificationsEnabled
        set(value) {
            modifiedKeys.add(UserConfig.NOTIFICATIONS_ENABLED)
            config = config.copy(notificationsEnabled = value)
        }

    var notificationsSound: String?
        get() = config.notificationsSound
        set(value) {
            modifiedKeys.add(UserConfig.NOTIFICATIONS_SOUND)
            config = config.copy(notificationsSound = value)
        }
}

class UserConfigService(
    backend: ConfigBackend,
    override var config: UserConfig = UserConfig()
) : ConfigServiceBase<UserConfig, UserEditorInterface>(backend) {
    override fun makeEditor(): UserEditorInterface = UserEditorInterface(config)

    override val configClass: Class<UserConfig> = UserConfig::class.java

    val notificationsSound: String?
        get() = config.notificationsSound

    val notificationsEnabled: Boolean
        get() = config.notificationsEnabled
}
