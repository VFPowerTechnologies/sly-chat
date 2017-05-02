package io.slychat.messenger.services.files

import java.io.InputStream
import java.io.OutputStream

//on android, paths are content URIs serialized as strings, or File paths
/** All methods here should be considered as incurring IO (either disk or IPC). Callers must take care to not call these on the main thread. */
interface PlatformFileAccess {
    fun getFileSize(path: String): Long

    fun getFileInfo(path: String): FileInfo

    /**
     * The given InputStream is valid only for the duration of the given body.
     *
     * This design is required to support usage of NSFileCoordinator on iOS.
     */
    fun <R> openFileForRead(path: String, body: (InputStream) -> R): R

    //TODO append
    fun openFileForWrite(path: String): OutputStream

    fun delete(path: String)
}