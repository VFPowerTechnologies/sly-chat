package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.services.SettingsService

class SettingsServiceImpl (activity: AppCompatActivity): SettingsService {
    companion object {
        val darkTheme = "dark"
        val lightTheme = "light"
    }

    data class NotificationConfig(
        var active: Boolean,
        var sound: String?,
        var soundName: String
    )

    data class MarketingConfig(
        var showInviteFriends: Boolean
    )

    data class AppearanceConfig(
        var theme: String?
    )

    enum class ConfigType { NOTIFICATION, MARKETING, APPEARANCE }

    private val app = AndroidApp.get(activity)
    private val configService = app.getUserComponent().userConfigService
    private val notificationService = app.notificationService

    var marketingConfig: MarketingConfig
    var notificationConfig : NotificationConfig
    var appearanceConfig : AppearanceConfig

    private var notificationListener : ((NotificationConfig) -> Unit)? = null
    private var marketingListener : ((MarketingConfig) -> Unit)? = null
    private var appearanceListener : ((AppearanceConfig) -> Unit)? = null

    var notificationEnabled = true
        set(value) {
            field = value
            notificationConfig.active = value
            updateNotificationConfig()
        }

    var notificationSound: String? = null
        set(value) {
            field = value
            notificationConfig.sound = value

            if (value !== null)
                notificationConfig.soundName = notificationService.getNotificationSoundDisplayName(value)

            updateNotificationConfig()
        }

    var marketingShowInviteFriends = true
        set(value) {
            field = value
            marketingConfig.showInviteFriends = value
            updateMarketingConfig()
        }

    var selectedTheme: String? = app.appComponent.appConfigService.appearanceTheme
        set(value) {
            field = value
            appearanceConfig.theme = value
            updateAppearanceConfig()
        }

    init {
        val sound = configService.notificationsSound
        var soundName = ""
        if (sound !== null)
            soundName = notificationService.getNotificationSoundDisplayName(sound)

        notificationConfig = NotificationConfig(configService.notificationsEnabled, configService.notificationsSound, soundName)
        notificationEnabled = configService.notificationsEnabled

        marketingConfig = MarketingConfig(configService.marketingShowInviteFriends)
        marketingShowInviteFriends = marketingConfig.showInviteFriends

        appearanceConfig = AppearanceConfig(selectedTheme)
    }

    override fun addNotificationConfigListener(listener: (NotificationConfig) -> Unit) {
        notificationListener = listener
    }

    override fun addMarketingConfigListener(listener: (MarketingConfig) -> Unit) {
        marketingListener = listener
    }

    override fun addAppearanceConfigListener(listener: (AppearanceConfig) -> Unit) {
        appearanceListener = listener
    }

    override fun clearConfigListener() {
        notificationListener = null
        appearanceListener = null
        marketingListener = null
    }

    override fun getShowInviteEnabled(): Boolean {
        return configService.marketingShowInviteFriends
    }

    private fun notifyConfigChange(type: ConfigType) {
        when (type) {
            ConfigType.NOTIFICATION -> { notificationListener?.invoke(notificationConfig) }
            ConfigType.MARKETING -> { marketingListener?.invoke(marketingConfig) }
            ConfigType.APPEARANCE -> { appearanceListener?.invoke(appearanceConfig) }
        }
    }

    private fun updateNotificationConfig() {
        configService.withEditor {
            notificationsEnabled = notificationConfig.active
            notificationsSound = notificationConfig.sound
        }

        notifyConfigChange(ConfigType.NOTIFICATION)
    }

    private fun updateMarketingConfig() {
        configService.withEditor {
            marketingShowInviteFriends = marketingConfig.showInviteFriends
        }

        notifyConfigChange(ConfigType.MARKETING)
    }

    private fun updateAppearanceConfig() {
        configService.withEditor {
            app.appComponent.appConfigService.withEditor {
                appearanceTheme = appearanceConfig.theme
            }
        }

        notifyConfigChange(ConfigType.APPEARANCE)
    }
}