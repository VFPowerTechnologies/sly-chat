package io.slychat.messenger.desktop

import io.slychat.messenger.core.*
import java.io.File

private fun getUserHome(): File =
    File(System.getProperty("user.home"))

private fun getUserAppDataDir(appName: String): File {
    val configDirOverride = System.getenv("SLY_CONFIG_DIR")
    if (configDirOverride != null)
        return File(configDirOverride)

    val home = getUserHome()

    return when (currentOs.type) {
        Os.Type.LINUX ->
            File(home, ".$appName")

        Os.Type.WINDOWS ->
            File(System.getenv("LOCALAPPDATA"), appName)

        Os.Type.OSX ->
            home / "Library" / "Application Support" / appName

        //TODO *BSD?

        else ->
            throw UnsupportedOsException(currentOs)
    }
}

class DesktopPlatformInfo : PlatformInfo {
    override val appFileStorageDirectory: File = getUserAppDataDir("sly")
}
