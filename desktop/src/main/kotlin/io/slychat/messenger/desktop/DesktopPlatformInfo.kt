package io.slychat.messenger.desktop

import io.slychat.messenger.core.PlatformInfo
import java.io.File

fun getUserHome(): File =
    File(System.getProperty("user.home"))

fun getUserConfigDir(appName: String): File {
    val home = getUserHome()

    val os = System.getProperty("os.name")
    return when {
        os == "Linux" ->
            File(File(home, ".config"), appName)

        os.startsWith("Windows") ->
            File(System.getenv("LOCALAPPDATA"), appName)

        //TODO OSX, *BSD?

        else ->
            throw RuntimeException("Unsupported OS: $os")
    }
}

class DesktopPlatformInfo : PlatformInfo {
    override val appFileStorageDirectory: File = getUserConfigDir("sly")

    override val dataFileStorageDirectory: File = File(appFileStorageDirectory, "data")
}