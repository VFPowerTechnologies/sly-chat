package io.slychat.messenger.core

import java.io.File

/** Platform-specific information. */
interface PlatformInfo {
    /** Location for the app to store its data */
    val appFileStorageDirectory: File

    /** Location of cache data. */
    val cacheDirectory: File
}
