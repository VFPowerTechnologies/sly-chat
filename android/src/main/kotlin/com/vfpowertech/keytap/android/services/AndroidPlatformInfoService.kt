package com.vfpowertech.keytap.android.services

import com.vfpowertech.keytap.services.ui.UIPlatformInfo
import com.vfpowertech.keytap.services.ui.PlatformInfoService

class AndroidPlatformInfoService : PlatformInfoService {
    override fun getInfo(): UIPlatformInfo {
        return UIPlatformInfo(UIPlatformInfo.PLATFORM_ANDROID, UIPlatformInfo.OS_ANDROID)
    }
}