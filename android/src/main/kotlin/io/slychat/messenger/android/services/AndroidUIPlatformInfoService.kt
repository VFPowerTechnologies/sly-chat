package io.slychat.messenger.android.services

import io.slychat.messenger.services.ui.UIPlatformInfo
import io.slychat.messenger.services.ui.UIPlatformInfoService

class AndroidUIPlatformInfoService : UIPlatformInfoService {
    override fun getInfo(): UIPlatformInfo {
        return UIPlatformInfo(UIPlatformInfo.Companion.PLATFORM_ANDROID, UIPlatformInfo.Companion.OS_ANDROID)
    }
}