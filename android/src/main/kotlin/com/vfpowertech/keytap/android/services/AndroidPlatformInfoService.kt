package com.vfpowertech.keytap.android.services

import com.vfpowertech.keytap.ui.services.UIPlatformInfo
import com.vfpowertech.keytap.ui.services.PlatformInfoService

class AndroidPlatformInfoService : PlatformInfoService {
    override fun getInfo(): UIPlatformInfo {
        return UIPlatformInfo(UIPlatformInfo.PLATFORM_ANDROID, UIPlatformInfo.OS_ANDROID)
    }
}