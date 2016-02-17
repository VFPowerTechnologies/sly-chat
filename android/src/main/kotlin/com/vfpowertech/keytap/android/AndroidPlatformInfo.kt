package com.vfpowertech.keytap.android

import android.content.Context
import com.vfpowertech.keytap.core.PlatformInfo
import java.io.File

class AndroidPlatformInfo(context: Context) : PlatformInfo {
    override val appFileStorageDirectory: File = File(context.applicationInfo.dataDir)
    override val dataFileStorageDirectory: File = File(appFileStorageDirectory, "data")
}