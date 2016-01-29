package com.vfpowertech.keytap.android.services

import com.vfpowertech.keytap.ui.services.PlatformInfo
import com.vfpowertech.keytap.ui.services.PlatformInfoService

class AndroidPlatformInfoService : PlatformInfoService {
    override fun getInfo(): PlatformInfo {
        return PlatformInfo(PlatformInfo.PLATFORM_ANDROID, PlatformInfo.OS_ANDROID)
    }
}