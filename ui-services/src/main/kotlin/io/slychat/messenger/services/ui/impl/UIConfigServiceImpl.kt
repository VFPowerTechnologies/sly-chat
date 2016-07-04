package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.ui.UIAppConfig
import io.slychat.messenger.services.ui.UIConfigService

class UIConfigServiceImpl(
    private val appConfigService: AppConfigService
) : UIConfigService {
    override fun getAppConfig(): UIAppConfig {
        return UIAppConfig(appConfigService.loginRememberMe)
    }
}