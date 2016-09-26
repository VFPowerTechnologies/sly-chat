package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*
import java.util.concurrent.TimeUnit

//paths are strings because android uses android.net.Uri for representing files (not all of which are convertable to
//real paths, eg content://settings/system/ringtone)
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserConfig(
    val formatVersion: Int = 1,
    val notificationsEnabled: Boolean = true,
    val notificationsSound: String? = null,
    val messagingLastTtl: Long = TimeUnit.SECONDS.toMillis(10)
) {
    companion object {
        private fun join(parent: String, child: String): String = "$parent.$child"

        val NOTIFICATIONS = "user.notifications"

        val NOTIFICATIONS_SOUND = join(NOTIFICATIONS, "sound")
        val NOTIFICATIONS_ENABLED = join(NOTIFICATIONS, "enabled")

        val MESSAGING = "user.messaging"

        val MESSAGING_LAST_TTL = join(MESSAGING, "lastTtl")
    }

    init {
        require(messagingLastTtl >= 0) { "messagingLastTtl must be >= 0, got $messagingLastTtl" }
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

    var messagingLastTtl: Long
        get() = config.messagingLastTtl
        set(value) {
            modifiedKeys.add(UserConfig.MESSAGING_LAST_TTL)
            config = config.copy(messagingLastTtl = value)
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

    val messagingLastTtl: Long
        get() = config.messagingLastTtl
}
