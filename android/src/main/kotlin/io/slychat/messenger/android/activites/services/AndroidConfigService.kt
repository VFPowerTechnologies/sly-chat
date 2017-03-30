package io.slychat.messenger.android.activites.services

import io.slychat.messenger.android.activites.services.impl.AndroidConfigServiceImpl
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.config.ConvoTTLSettings

interface AndroidConfigService {

    fun addNotificationConfigListener(listener: (AndroidConfigServiceImpl.NotificationConfig) -> Unit)

    fun addMarketingConfigListener(listener: (AndroidConfigServiceImpl.MarketingConfig) -> Unit)

    fun addAppearanceConfigListener(listener: (AndroidConfigServiceImpl.AppearanceConfig) -> Unit)

    fun clearConfigListener()

    fun getShowInviteEnabled(): Boolean

    fun getConvoTTLSettings(conversationId: ConversationId): ConvoTTLSettings?

    fun setConvoTTLSettings(conversationId: ConversationId, convoTTLSettings: ConvoTTLSettings)
}