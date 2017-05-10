package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.crypto.DecryptInputStream
import io.slychat.messenger.core.crypto.EncryptOutputStream
import io.slychat.messenger.core.crypto.HKDFInfoList
import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.ciphers.deriveKey
import io.slychat.messenger.core.div
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * Manages the on-disk cache.
 */
class AttachmentCacheImpl(cacheRoot: File) : AttachmentCache {
    //this is a flat dir; images are named <fileId>_(original|<resolution>)
    private val pendingDir = cacheRoot / "pending"

    //this is a nested dir, with <fileId>/original, <resolution> files
    //thumbnails will be in whatever format the thumbnail generator generates
    private val activeDir = cacheRoot / "active"

    override fun init() {
        pendingDir.mkdirs()
        activeDir.mkdirs()
    }

    private fun recursivelyDeleteDir(dir: File) {
        if (!dir.exists())
            return

        require(dir.isDirectory) { "$dir is not a directory" }

        val dirs = ArrayDeque<File>()
        dirs.add(dir)

        while (dirs.isNotEmpty()) {
            val path = dirs.first
            val contents = path.listFiles()

            if (contents.isNotEmpty()) {
                contents.forEach { file ->
                    if (file.isDirectory) {
                        dirs.addFirst(file)
                    }
                    else if (file.isFile) {
                        file.delete()
                    }
                    else
                        throw RuntimeException("$file is not a file or directory")
                }
            }
            else {
                path.delete()
                dirs.pop()
            }
        }
    }

    private fun getPendingPathForOriginal(fileId: String): File {
        return pendingDir / "${fileId}_original"
    }

    private fun getPendingPathForThumbnail(fileId: String, resolution: Int): File {
        return pendingDir / "${fileId}_$resolution"
    }

    private fun getActivePathForThumbnail(fileId: String, resolution: Int): File {
        return getActiveCacheDirForFile(fileId) / resolution.toString()
    }

    private fun getActivePathForOriginal(fileId: String): File {
        return getActiveCacheDirForFile(fileId) / "original"
    }

    private fun getActiveCacheDirForFile(fileId: String): File {
        return activeDir / fileId
    }

    override fun getPendingPathForFile(fileId: String): File {
        return getPendingPathForOriginal(fileId)
    }

    override fun getFinalPathForFile(fileId: String): File {
        return getActivePathForOriginal(fileId)
    }

    override fun filterPresent(fileIds: Set<String>): Promise<Set<String>, Exception> {
        return task {
            fileIds.filterTo(HashSet()) { (activeDir / it).isFile || (pendingDir / it).isFile }
        }
    }

    //we encrypt thumbnails using a different key to avoid reusing key material
    private fun deriveThumbnailKey(fileKey: Key, cipher: Cipher, fileId: String, resolution: Int): Key {
        return deriveKey(fileKey, HKDFInfoList.thumbnail(fileId, resolution), cipher.keySizeBits)
    }

    override fun getThumbnailInputStream(fileId: String, resolution: Int, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream? {
        val path = getActivePathForThumbnail(fileId, resolution)

        val thumbnailKey = deriveThumbnailKey(fileKey, cipher, fileId, resolution)

        return try {
            DecryptInputStream(cipher, thumbnailKey, path.inputStream(), chunkSize)
        }
        catch (e: FileNotFoundException) {
            null
        }
    }

    private fun getThumbnailOutputStream(fileId: String, resolution: Int, fileKey: Key, cipher: Cipher, chunkSize: Int): OutputStream {
        val path = getPendingPathForThumbnail(fileId, resolution)

        return EncryptOutputStream(cipher, fileKey, chunkSize, path.outputStream())
    }

    override fun isOriginalPresent(fileId: String): Boolean {
        return getActiveCacheDirForFile(fileId).isDirectory
    }

    override fun getThumbnailGenerationStreams(fileId: String, resolution: Int, fileKey: Key, cipher: Cipher, chunkSize: Int): ThumbnailWriteStreams? {
        val inputStream = getOriginalImageInputStream(fileId, fileKey, cipher, chunkSize) ?: return null
        val thumbnailKey = deriveThumbnailKey(fileKey, cipher, fileId, resolution)

        val outputStream = try {
            getThumbnailOutputStream(fileId, resolution, thumbnailKey, cipher, chunkSize)
        }
        catch (t: Throwable) {
            inputStream.close()
            throw t
        }

        return ThumbnailWriteStreams(inputStream, outputStream)
    }

    override fun getOriginalImageInputStream(fileId: String, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream? {
        val path = getActivePathForOriginal(fileId)

        return try {
            DecryptInputStream(
                cipher,
                fileKey,
                path.inputStream(),
                chunkSize
            )
        }
        catch (e: FileNotFoundException) {
            null
        }
    }

    override fun delete(fileIds: List<String>): Promise<Unit, Exception> = task {
        fileIds.forEach {
            recursivelyDeleteDir(getActiveCacheDirForFile(it))
        }

        deletePendingFilesFor(fileIds)
    }

    private fun deletePendingFilesFor(fileIds: List<String>) {
        pendingDir.listFiles().forEach { path ->
            for (fileId in fileIds) {
                if (path.name.startsWith(fileId)) {
                    path.delete()
                }
            }
        }
    }

    override fun markOriginalComplete(fileIds: List<String>): Promise<Unit, Exception> = task {
        fileIds.forEach {
            //can't use nio path on android, so just assume this doesn't fail since we can't tell why it failed
            getActiveCacheDirForFile(it).mkdir()
            getPendingPathForOriginal(it).renameTo(getActivePathForOriginal(it))
        }
    }

    override fun markThumbnailComplete(fileId: String, resolution: Int): Promise<Unit, Exception> = task {
        //this is mostly here so delete is testable (otherwise move'll fail and we won't know the pending file didn't
        //get deleted)
        getActiveCacheDirForFile(fileId).mkdirs()
        getPendingPathForThumbnail(fileId, resolution).renameTo(getActivePathForThumbnail(fileId, resolution))

        Unit
    }
}