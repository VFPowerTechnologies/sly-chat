package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.ui.UIConfigService

class UIConfigServiceImpl(
    private val appConfigService: AppConfigService
) : UIConfigService {
    override fun getLoginRememberMe(): Boolean {
        return appConfigService.loginRememberMe
    }

    override fun setLoginRememberMe(v: Boolean) {
        appConfigService.withEditor {
            loginRememberMe = v
        }
    }
}