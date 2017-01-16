package io.slychat.messenger.ios

import io.slychat.messenger.services.ui.UIPlatformInfo
import io.slychat.messenger.services.ui.UIPlatformInfoService

class IOSUIPlatformInfoService : UIPlatformInfoService {
    override fun getInfo(): UIPlatformInfo {
        return UIPlatformInfo(UIPlatformInfo.PLATFORM_IOS, "ios")
    }
}