package com.vfpowertech.keytap.desktop.services

import com.vfpowertech.keytap.ui.services.PlatformInfo
import com.vfpowertech.keytap.ui.services.PlatformInfoService

class DesktopPlatformInfoService : PlatformInfoService {
    override fun getInfo(): PlatformInfo {
        val osName = System.getProperty("os.name")
        val os = when {
            osName == "Linux" -> PlatformInfo.OS_LINUX
            osName.startsWith("Windows") -> PlatformInfo.OS_WINDOWS
            else -> PlatformInfo.OS_UNKNOWN
        }

        return PlatformInfo(PlatformInfo.PLATFORM_DESKTOP, os)
    }
}