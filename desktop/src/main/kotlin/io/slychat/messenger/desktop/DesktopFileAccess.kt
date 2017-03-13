package io.slychat.messenger.desktop

import io.slychat.messenger.services.files.FileInfo
import io.slychat.messenger.services.files.PlatformFileAccess
import java.io.*

class DesktopFileAccess : PlatformFileAccess {
    override fun getFileSize(path: String): Long {
        return File(path).length()
    }

    override fun getFileInfo(path: String): FileInfo {
        val file = File(path)
        return FileInfo(
                file.name,
                file.length(),
                //TODO
                "*/*"
            )
    }

    override fun openFileForRead(path: String): InputStream {
        return FileInputStream(path)
    }

    override fun openFileForWrite(path: String): OutputStream {
        return FileOutputStream(path)
    }
}