package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIConfigService
import io.slychat.messenger.services.ui.UINotificationConfig
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.util.*

class UIConfigServiceImpl(
    userSessionAvailable: Observable<UserComponent?>,
    private val appConfigService: AppConfigService,
    private val platformNotificationService: PlatformNotificationService
) : UIConfigService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val notificationConfigChangeListeners = ArrayList<(UINotificationConfig) -> Unit>()

    private val subscriptions = CompositeSubscription()

    private var userConfigService: UserConfigService? = null

    init {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent == null) {
            userConfigService = null
            subscriptions.clear()
        }
        else {
            userConfigService = userComponent.userConfigService

            subscriptions.add(userComponent.userConfigService.updates.subscribe { onUserConfigUpdate(it) })

            pushInitialConfigs()
        }
    }

    private fun onUserConfigUpdate(updates: Collection<String>) {
        var updateNotifications = false

        log.debug("User configuration updated: {}", updates)

        updates.forEach { key ->
            if (key.startsWith(UserConfig.NOTIFICATIONS))
                updateNotifications = true
        }

        if (updateNotifications)
            notifyNotificationConfigChangeListeners()
    }

    private fun pushInitialConfigs() {
        notifyNotificationConfigChangeListeners()
    }

    private fun getUserConfigServiceOrThrow(): UserConfigService {
        return userConfigService ?: error("Not logged in")
    }

    private fun getUINotificationConfig(): UINotificationConfig {
        val userConfigService = getUserConfigServiceOrThrow()

        val soundName = userConfigService.notificationsSound?.let {
            platformNotificationService.getNotificationSoundDisplayName(it)
        }

        return UINotificationConfig(
            userConfigService.notificationsEnabled,
            userConfigService.notificationsSound,
            soundName
        )
    }

    override fun setNotificationConfig(config: UINotificationConfig) {
        val userConfigService = getUserConfigServiceOrThrow()

        userConfigService.withEditor {
            notificationsEnabled = config.enabled
            notificationsSound = config.sound
        }
    }

    override fun addNotificationConfigChangeListener(listener: (UINotificationConfig) -> Unit) {
        notificationConfigChangeListeners.add(listener)

        if (userConfigService != null)
            listener(getUINotificationConfig())
    }

    private fun notifyNotificationConfigChangeListeners() {
        val uiNotificationConfig = getUINotificationConfig()

        notificationConfigChangeListeners.forEach { it(uiNotificationConfig) }
    }

    override fun clearListeners() {
        notificationConfigChangeListeners.clear()
    }

    override fun getLoginRememberMe(): Boolean {
        return appConfigService.loginRememberMe
    }

    override fun setLoginRememberMe(v: Boolean) {
        appConfigService.withEditor {
            loginRememberMe = v
        }
    }

    override fun getLastMessageTtl(): Long {
        return getUserConfigServiceOrThrow().messagingLastTtl
    }

    override fun setLastMessageTtl(v: Long) {
        getUserConfigServiceOrThrow().withEditor {
            messagingLastTtl = v
        }
    }
}