package io.slychat.messenger.desktop

import io.slychat.messenger.services.files.FileInfo
import io.slychat.messenger.services.files.PlatformFileAccess
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.*

class DesktopFileAccess : PlatformFileAccess {
    override fun getFileSize(path: String): Promise<Long, Exception> {
        return task { File(path).length() }
    }

    override fun getFileDisplayName(path: String): Promise<String, Exception> {
        return Promise.ofSuccess(File(path).name)
    }

    override fun getMimeType(path: String): Promise<String, Exception> {
        //TODO integrate tika
        return Promise.of("*/*")
    }

    override fun getFileInfo(path: String): Promise<FileInfo, Exception> {
        val file = File(path)
        return task {
            FileInfo(
                file.name,
                file.length(),
                //TODO
                "*/*"
            )
        }
    }

    override fun openFileForRead(path: String): Promise<InputStream, Exception> {
        return Promise.of(FileInputStream(path))
    }

    override fun openFileForWrite(path: String): Promise<OutputStream, Exception> {
        return Promise.of(FileOutputStream(path))
    }

    //we don't need to do anything on desktop, since the file can be created on write
    override fun createFile(path: String): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }
}