package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationIdKeyDeserializer
import io.slychat.messenger.core.persistence.ConversationIdKeySerializer
import java.util.*

//paths are strings because android uses android.net.Uri for representing files (not all of which are convertable to
//real paths, eg content://settings/system/ringtone)
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserConfig(
    val formatVersion: Int = 1,

    val notificationsEnabled: Boolean = true,

    val notificationsSound: String? = null,

    @field:JsonDeserialize(keyUsing = ConversationIdKeyDeserializer::class)
    @get:JsonSerialize(keyUsing = ConversationIdKeySerializer::class)
    val messagingConvoTTLSettings: Map<ConversationId, ConvoTTLSettings> = emptyMap(),

    val marketingShowInviteFriends: Boolean = false,

    val transfersSimulDownloads: Int = 3,

    val transfersSimulUploads: Int = 3
) {
    companion object {
        private fun join(parent: String, child: String): String = "$parent.$child"

        val NOTIFICATIONS = "user.notifications"

        val NOTIFICATIONS_SOUND = join(NOTIFICATIONS, "sound")
        val NOTIFICATIONS_ENABLED = join(NOTIFICATIONS, "enabled")

        val MESSAGING = "user.messaging"

        val MESSAGING_CONVO_TTL_SETTINGS = join(MESSAGING, "convoTTLSettings")

        val MARKETING = "user.marketing"

        val MARKETING_SHOW_INVITE_FRIENDS = join(MARKETING, "showInviteFriends")

        val TRANSFERS = "user.transfers"

        val TRANSFERS_SIMUL_DOWNLOADS = join(TRANSFERS, "simulDownloads")

        val TRANSFERS_SIMUL_UPLOADS = join(TRANSFERS, "simulUploads")
    }
}

class UserEditorInterface(override var config: UserConfig) : ConfigServiceBase.EditorInterface<UserConfig> {
    override val modifiedKeys = HashSet<String>()

    var notificationsEnabled: Boolean
        get() = config.notificationsEnabled
        set(value) {
            if (value != notificationsEnabled) {
                modifiedKeys.add(UserConfig.NOTIFICATIONS_ENABLED)
                config = config.copy(notificationsEnabled = value)
            }
        }

    var notificationsSound: String?
        get() = config.notificationsSound
        set(value) {
            if (value != notificationsSound) {
                modifiedKeys.add(UserConfig.NOTIFICATIONS_SOUND)
                config = config.copy(notificationsSound = value)
            }
        }

    var messagingConvoTTLSettings: Map<ConversationId, ConvoTTLSettings>
        get() = config.messagingConvoTTLSettings
        set(value) {
            if (value != messagingConvoTTLSettings) {
                modifiedKeys.add(UserConfig.MESSAGING_CONVO_TTL_SETTINGS)
                config = config.copy(messagingConvoTTLSettings = value)
            }
        }

    var marketingShowInviteFriends: Boolean
        get() = config.marketingShowInviteFriends
        set(value) {
            if (value != marketingShowInviteFriends) {
                modifiedKeys.add(UserConfig.MARKETING_SHOW_INVITE_FRIENDS)
                config = config.copy(marketingShowInviteFriends = value)
            }
        }

    var transfersSimulDownloads: Int
        get() = config.transfersSimulDownloads
        set(value) {
            if (value != transfersSimulDownloads) {
                modifiedKeys.add(UserConfig.TRANSFERS_SIMUL_DOWNLOADS)
                config = config.copy(transfersSimulDownloads = value)
            }
        }

    var transfersSimulUploads: Int
        get() = config.transfersSimulUploads
        set(value) {
            if (value != transfersSimulUploads) {
                modifiedKeys.add(UserConfig.TRANSFERS_SIMUL_UPLOADS)
                config = config.copy(transfersSimulUploads = value)
            }
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

    val messagingConvoLastTTL: Map<ConversationId, ConvoTTLSettings>
        get() = config.messagingConvoTTLSettings

    val marketingShowInviteFriends: Boolean
        get() = config.marketingShowInviteFriends

    val tranfersSimulDownloads: Int
        get() = config.transfersSimulDownloads

    val transfersSimulUploads: Int
        get() = config.transfersSimulUploads
}
