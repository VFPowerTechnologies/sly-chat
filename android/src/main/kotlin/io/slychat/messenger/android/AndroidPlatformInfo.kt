package io.slychat.messenger.android

import android.content.Context
import io.slychat.messenger.core.PlatformInfo
import java.io.File

class AndroidPlatformInfo(context: Context) : PlatformInfo {
    override val appFileStorageDirectory: File = File(context.applicationInfo.dataDir)
}