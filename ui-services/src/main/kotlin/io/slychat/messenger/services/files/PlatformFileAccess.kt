package io.slychat.messenger.services.files

import java.io.InputStream
import java.io.OutputStream

//on android, paths are content URIs serialized as strings, or File paths
/** All methods here should be considered as incurring IO (either disk or IPC). Callers must take care to not call these on the main thread. */
interface PlatformFileAccess {
    fun getFileSize(path: String): Long

    fun getFileInfo(path: String): FileInfo

    fun openFileForRead(path: String): InputStream

    //TODO append
    fun openFileForWrite(path: String): OutputStream
}