package io.slychat.messenger.android.activites.services

import io.slychat.messenger.android.activites.services.impl.SettingsServiceImpl
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.config.ConvoTTLSettings

interface SettingsService {

    fun addNotificationConfigListener(listener: (SettingsServiceImpl.NotificationConfig) -> Unit)

    fun addMarketingConfigListener(listener: (SettingsServiceImpl.MarketingConfig) -> Unit)

    fun addAppearanceConfigListener(listener: (SettingsServiceImpl.AppearanceConfig) -> Unit)

    fun clearConfigListener()

    fun getShowInviteEnabled(): Boolean

    fun getConvoTTLSettings(conversationId: ConversationId): ConvoTTLSettings?

    fun setConvoTTLSettings(conversationId: ConversationId, convoTTLSettings: ConvoTTLSettings)
}