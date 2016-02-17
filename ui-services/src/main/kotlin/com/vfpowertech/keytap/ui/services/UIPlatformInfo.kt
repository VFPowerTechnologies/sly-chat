package com.vfpowertech.keytap.ui.services

/**
 * Information regarding the underlying platform.
 *
 * @property name Name of platform. One of the PLATFORM_ values.
 * @property os Name of OS. One of the OS_ values.
 * @constructor
 */
data class UIPlatformInfo(val name: String, val os: String) {
    companion object {
        val PLATFORM_ANDROID = "android"
        val PLATFORM_DESKTOP = "desktop"
        val PLATFORM_IOS = "ios"
        val PLATFORM_UNKNOWN = "unknown"

        val OS_ANDROID = "android"
        val OS_IOS = "ios"
        val OS_LINUX = "linux"
        val OS_OSX = "osx"
        val OS_WINDOWS = "windows"
        val OS_UNKNOWN = "unknown"
    }
}