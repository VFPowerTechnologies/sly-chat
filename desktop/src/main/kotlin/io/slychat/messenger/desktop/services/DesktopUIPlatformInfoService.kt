package io.slychat.messenger.desktop.services

import io.slychat.messenger.services.ui.UIPlatformInfoService
import io.slychat.messenger.services.ui.UIPlatformInfo

class DesktopUIPlatformInfoService : UIPlatformInfoService {
    override fun getInfo(): UIPlatformInfo {
        val osName = System.getProperty("os.name")
        val os = when {
            osName == "Linux" -> UIPlatformInfo.OS_LINUX
            osName.startsWith("Windows") -> UIPlatformInfo.OS_WINDOWS
            else -> UIPlatformInfo.OS_UNKNOWN
        }

        return UIPlatformInfo(UIPlatformInfo.PLATFORM_DESKTOP, os)
    }
}