package com.vfpowertech.keytap.desktop.services

import com.vfpowertech.keytap.ui.services.PlatformInfoService
import com.vfpowertech.keytap.ui.services.UIPlatformInfo

class DesktopPlatformInfoService : PlatformInfoService {
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