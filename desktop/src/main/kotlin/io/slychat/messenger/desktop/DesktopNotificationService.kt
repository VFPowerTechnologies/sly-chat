package io.slychat.messenger.desktop

import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.di.UserComponent
import javafx.scene.media.AudioClip
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.io.File
import java.net.URI

class DesktopNotificationService(
    private val audioPlayback: AudioPlayback,
    private val notificationDisplay: NotificationDisplay
) : PlatformNotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    private var userConfigService: UserConfigService? = null

    private val subscriptions = CompositeSubscription()

    private var messageNotificationAudioClip: AudioClip? = null

    fun init(userSessionAvailable: Observable<UserComponent?>) {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent == null) {
            subscriptions.clear()
            userConfigService = null
            refreshClipCache()
        }
        else {
            userConfigService = userComponent.userConfigService

            subscriptions.add(userComponent.userConfigService.updates.subscribe { onUserConfigUpdate(it) })

            refreshClipCache()
        }
    }

    private fun refreshClipCache() {
        messageNotificationAudioClip = null

        val userConfigService = this.userConfigService ?: return

        val soundPath = userConfigService.notificationsSound ?: return

        val pathUri = soundPath

        val audioClip = try {
            AudioClip(pathUri)
        }
        catch (e: Exception) {
            log.warn("Invalid notification path: {}", pathUri)
            return
        }

        messageNotificationAudioClip = audioClip
    }

    private fun onUserConfigUpdate(keys: Collection<String>) {
        val doRefresh = keys.contains(UserConfig.NOTIFICATIONS_SOUND)

        if (doRefresh)
            refreshClipCache()
    }

    override fun updateNotificationState(notificationState: NotificationState) {
        notificationState.state.forEach {
            if (it.hasNew)
                displayNotification(it.conversationDisplayInfo)
        }
    }

    override fun getNotificationSoundDisplayName(soundUri: String): String {
        val name = File(URI(soundUri).path).name

        return if (name.contains('.'))
            name.substring(0, name.lastIndexOf('.'))
        else
            name
    }

    private fun displayNotification(conversationDisplayInfo: ConversationDisplayInfo) {
        if (conversationDisplayInfo.unreadCount == 0)
            return

        val lastMessageData = conversationDisplayInfo.lastMessageData

        log.debug(
            "New notification for {}: count={}",
            conversationDisplayInfo.conversationId,
            conversationDisplayInfo.unreadCount
        )

        if (lastMessageData != null) {
            val isExpirable = lastMessageData.message == null

            val extra = if (isExpirable) "secret " else ""

            playNotificationSound()

            notificationDisplay.displayNotification("Sly Chat", "You have a new ${extra}message from ${lastMessageData.speakerName}")
        }
    }

    private fun playNotificationSound() {
        val audioClip = messageNotificationAudioClip ?: return
        audioPlayback.play(audioClip)
    }
}