package io.slychat.messenger.ios

import apple.foundation.c.Foundation
import io.slychat.messenger.core.PlatformInfo
import io.slychat.messenger.core.div
import java.io.File

//https://developer.apple.com/library/content/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html
class IOSPlatformInfo : PlatformInfo {
    override val appFileStorageDirectory: File

    override val cacheDirectory: File

    init {
        val home = File(Foundation.NSHomeDirectory())

        val library = home / "Library"

        appFileStorageDirectory = library / "Application Support"
        cacheDirectory = library / "Caches"
    }
}