package io.slychat.messenger.desktop

import io.slychat.messenger.core.*
import java.io.File

fun getUserHome(): File =
    File(System.getProperty("user.home"))

fun getUserConfigDir(appName: String): File {
    val home = getUserHome()

    return when (currentOs.type) {
        Os.Type.LINUX ->
            File(File(home, ".config"), appName)

        Os.Type.WINDOWS ->
            File(System.getenv("LOCALAPPDATA"), appName)

        Os.Type.OSX ->
            home / "Library" / "Preferences" / appName

        //TODO *BSD?

        else ->
            throw UnsupportedOsException(currentOs)
    }
}

class DesktopPlatformInfo : PlatformInfo {
    override val appFileStorageDirectory: File = getUserConfigDir("sly")

    override val dataFileStorageDirectory: File = File(appFileStorageDirectory, "data")
}
