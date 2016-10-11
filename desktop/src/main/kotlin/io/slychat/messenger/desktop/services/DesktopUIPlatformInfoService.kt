package io.slychat.messenger.desktop.services

import io.slychat.messenger.core.Os
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.services.ui.UIPlatformInfo
import io.slychat.messenger.services.ui.UIPlatformInfoService

class DesktopUIPlatformInfoService : UIPlatformInfoService {
    override fun getInfo(): UIPlatformInfo {
        val os = when (currentOs.type) {
            Os.Type.LINUX -> UIPlatformInfo.OS_LINUX
            Os.Type.WINDOWS -> UIPlatformInfo.OS_WINDOWS
            Os.Type.OSX -> UIPlatformInfo.OS_OSX
            else -> UIPlatformInfo.OS_UNKNOWN
        }

        return UIPlatformInfo(UIPlatformInfo.PLATFORM_DESKTOP, os)
    }
}