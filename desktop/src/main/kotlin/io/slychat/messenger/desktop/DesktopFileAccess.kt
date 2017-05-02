package io.slychat.messenger.desktop

import io.slychat.messenger.services.files.FileInfo
import io.slychat.messenger.services.files.PlatformFileAccess
import org.apache.tika.Tika
import java.io.*

class DesktopFileAccess : PlatformFileAccess {
    private val tika = Tika()

    override fun getFileSize(path: String): Long {
        return File(path).length()
    }

    override fun getFileInfo(path: String): FileInfo {
        val file = File(path)
        return FileInfo(
                file.name,
                file.length(),
                tika.detect(file)
            )
    }

    override fun <R> openFileForRead(path: String, body: (InputStream) -> R): R {
        return FileInputStream(path).use(body)
    }

    override fun openFileForWrite(path: String): OutputStream {
        return FileOutputStream(path)
    }

    override fun delete(path: String) {
        File(path).delete()
    }
}