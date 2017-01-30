package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.config.*
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIAppearanceConfig
import io.slychat.messenger.services.ui.UIConfigService
import io.slychat.messenger.services.ui.UIMarketingConfig
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
    private val appearanceConfigChangeListeners = ArrayList<(UIAppearanceConfig) -> Unit>()
    private val marketingConfigChangeListeners = ArrayList<(UIMarketingConfig) -> Unit>()

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

    private fun onAppConfigUpdate(updates: Collection<String>) {
        var updateAppearance = false

        log.debug("App configuration updated: {}", updates)

        updates.forEach { key ->
            if (key.startsWith(AppConfig.APPEARANCE))
                updateAppearance = true
        }

        if (updateAppearance)
            notifyAppearanceConfigChangeListeners()
    }

    private fun getUIAppearanceConfig(): UIAppearanceConfig {
        return UIAppearanceConfig(
            appConfigService.appearanceTheme
        )
    }

    private fun notifyAppearanceConfigChangeListeners() {
        val uiAppearanceConfig = getUIAppearanceConfig()

        appearanceConfigChangeListeners.forEach { it(uiAppearanceConfig) }
    }

    private fun onUserConfigUpdate(updates: Collection<String>) {
        var updateNotifications = false
        var updateMarketing = false

        log.debug("User configuration updated: {}", updates)

        updates.forEach { key ->
            if (key.startsWith(UserConfig.NOTIFICATIONS))
                updateNotifications = true
            else if (key.startsWith(UserConfig.MARKETING))
                updateMarketing = true
        }

        if (updateNotifications)
            notifyNotificationConfigChangeListeners()

        if (updateMarketing)
            notifyMarketingConfigChangeListeners()
    }

    private fun pushInitialConfigs() {
        notifyNotificationConfigChangeListeners()
        notifyAppearanceConfigChangeListeners()
        notifyMarketingConfigChangeListeners()
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

    private fun notifyMarketingConfigChangeListeners() {
        val uiMarketingConfig = getUIMarketingConfg()

        marketingConfigChangeListeners.forEach { it(uiMarketingConfig) }
    }

    override fun setAppearanceConfig(config: UIAppearanceConfig) {
        appConfigService.withEditor {
            appearanceTheme = config.theme
        }
    }

    override fun addAppearanceConfigChangeListener(listener: (UIAppearanceConfig) -> Unit) {
        appearanceConfigChangeListeners.add(listener)

        listener(getUIAppearanceConfig())
    }

    private fun getUIMarketingConfg(): UIMarketingConfig {
        val userConfigService = getUserConfigServiceOrThrow()

        return UIMarketingConfig(userConfigService.marketingShowInviteFriends)
    }

    override fun setMarketingConfig(config: UIMarketingConfig) {
        getUserConfigServiceOrThrow().withEditor {
            marketingShowInviteFriends = config.showInviteFriends
        }
    }

    override fun addMarketingConfigChangeListener(listener: (UIMarketingConfig) -> Unit) {
        marketingConfigChangeListeners.add(listener)
        if (userConfigService != null)
            listener(getUIMarketingConfg())
    }

    override fun clearListeners() {
        notificationConfigChangeListeners.clear()
        appearanceConfigChangeListeners.clear()
        marketingConfigChangeListeners.clear()
    }

    override fun getLoginRememberMe(): Boolean {
        return appConfigService.loginRememberMe
    }

    override fun setLoginRememberMe(v: Boolean) {
        appConfigService.withEditor {
            loginRememberMe = v
        }
    }

    override fun getConvoTTLSettings(conversationId: ConversationId): ConvoTTLSettings? {
        return getUserConfigServiceOrThrow().messagingConvoLastTTL[conversationId]
    }

    override fun setConvoTTLSettings(conversationId: ConversationId, convoTTLSettings: ConvoTTLSettings) {
        getUserConfigServiceOrThrow().withEditor {
            println(convoTTLSettings)
            messagingConvoTTLSettings += conversationId to convoTTLSettings
        }
    }
}