package com.vfpowertech.keytap.core

import java.io.File

/** Platform-specific information. */
interface PlatformInfo {
    /** Location for the app to store its data */
    val appFileStorageDirectory: File

    /** Location for storing user files */
    val dataFileStorageDirectory: File
}
