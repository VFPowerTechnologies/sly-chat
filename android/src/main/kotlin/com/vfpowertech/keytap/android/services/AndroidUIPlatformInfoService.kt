package com.vfpowertech.keytap.android.services

import com.vfpowertech.keytap.services.ui.UIPlatformInfo
import com.vfpowertech.keytap.services.ui.UIPlatformInfoService

class AndroidUIPlatformInfoService : UIPlatformInfoService {
    override fun getInfo(): UIPlatformInfo {
        return UIPlatformInfo(UIPlatformInfo.PLATFORM_ANDROID, UIPlatformInfo.OS_ANDROID)
    }
}