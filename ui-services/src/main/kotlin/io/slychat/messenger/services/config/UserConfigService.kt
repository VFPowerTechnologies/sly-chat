package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.io.File
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserConfig(
    val formatVersion: Int = 1,
    val notificationsEnabled: Boolean = true,
    val notificationsSound: File = File("/sound"),
    val notificationsDndMode: Boolean = false,
    val profileAvatar: File = File("/avatar")
) {
    companion object {
        private fun join(parent: String, child: String): String = "$parent.$child"

        val NOTIFICATIONS = "user.notifications"

        val NOTIFICATIONS_SOUND = join(NOTIFICATIONS, "sound")
        val NOTIFICATIONS_ENABLED = join(NOTIFICATIONS, "enabled")
        val NOTIFICATIONS_DNDMODE = join(NOTIFICATIONS, "dndMode")
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

    var notificationsSound: File
        get() = config.notificationsSound
        set(value) {
            modifiedKeys.add(UserConfig.NOTIFICATIONS_SOUND)
            config = config.copy(notificationsSound = value)
        }

    var notificationsDndMode: Boolean
        get() = config.notificationsDndMode
        set(value) {
            modifiedKeys.add(UserConfig.NOTIFICATIONS_DNDMODE)
            config = config.copy(notificationsDndMode = value)
        }
}

class UserConfigService(
    backend: ConfigBackend,
    override var config: UserConfig = UserConfig()
) : ConfigServiceBase<UserConfig, UserEditorInterface>(backend) {
    override fun makeEditor(): UserEditorInterface = UserEditorInterface(config)

    override val configClass: Class<UserConfig> = UserConfig::class.java

    val notificationsSound: File
        get() = config.notificationsSound

    val notificationsEnabled: Boolean
        get() = config.notificationsEnabled

    val notificationsDndMode: Boolean
        get() = config.notificationsDndMode
}
