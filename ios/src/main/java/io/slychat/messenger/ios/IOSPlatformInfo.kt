package io.slychat.messenger.ios

import apple.foundation.c.Foundation
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.div
import java.io.File

class IOSPlatformInfo : PlatformInfo {
    override val appFileStorageDirectory: File
        get() = File(Foundation.NSHomeDirectory()) / "Library" / "Application Support"
}