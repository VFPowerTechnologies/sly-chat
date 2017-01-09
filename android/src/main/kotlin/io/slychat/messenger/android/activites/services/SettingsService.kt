package io.slychat.messenger.android.activites.services

import io.slychat.messenger.android.activites.services.impl.SettingsServiceImpl

interface SettingsService {

    fun addNotificationConfigListener(listener: (SettingsServiceImpl.NotificationConfig) -> Unit)

    fun addMarketingConfigListener(listener: (SettingsServiceImpl.MarketingConfig) -> Unit)

    fun addAppearanceConfigListener(listener: (SettingsServiceImpl.AppearanceConfig) -> Unit)

    fun clearConfigListener()

    fun getShowInviteEnabled(): Boolean

    fun getLastMessageTtl(): Long

    fun setLastMessageTtl(ttl: Long)
}