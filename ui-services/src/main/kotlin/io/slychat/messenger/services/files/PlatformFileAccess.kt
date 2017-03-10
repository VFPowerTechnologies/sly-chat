package io.slychat.messenger.services.files

import nl.komponents.kovenant.Promise
import java.io.InputStream
import java.io.OutputStream

//on android, paths are URIs serialized as strings
//the openFileFor* methods use provides as this uses content providers on android, which can incur IPC or event
//network access depending on the provider
//should throw FileNotFoundException if path is invalid
interface PlatformFileAccess {
    fun getFileSize(path: String): Promise<Long, Exception>

    fun getFileDisplayName(path: String): Promise<String, Exception>

    fun getMimeType(path: String): Promise<String, Exception>

    fun getFileInfo(path: String): Promise<FileInfo, Exception>

    fun openFileForRead(path: String): Promise<InputStream, Exception>

    fun openFileForWrite(path: String): Promise<OutputStream, Exception>

    fun createFile(path: String): Promise<Unit, Exception>
}