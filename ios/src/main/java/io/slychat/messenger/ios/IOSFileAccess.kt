package io.slychat.messenger.ios

import apple.foundation.NSData
import apple.foundation.NSNumber
import apple.foundation.NSURL
import apple.foundation.c.Foundation.*
import apple.photos.PHAsset
import apple.photos.PHAssetResource
import apple.photos.PHImageManager
import apple.photos.PHImageRequestOptions
import io.slychat.messenger.services.files.FileInfo
import io.slychat.messenger.services.files.PlatformFileAccess
import nl.komponents.kovenant.Kovenant.deferred
import org.moe.natj.general.ptr.impl.PtrFactory
import java.io.*

private fun NSURL.getFileInfo(): FileInfo {
    val errPtr = newNSErrorPtr()
    val keys = nsarray(
        NSURLFileSizeKey(),
        NSURLTypeIdentifierKey(),
        NSURLNameKey()
    )

    val resources = promisedItemResourceValuesForKeysError(keys, errPtr)

    checkNSError(errPtr, "NSURL.resourceValuesForKeys")

    val size = resources[NSURLFileSizeKey()] as NSNumber
    val uti = resources[NSURLTypeIdentifierKey()] as String
    val name = resources[NSURLNameKey()] as String

    return FileInfo(
        name,
        size.longValue(),
        utiToMimeType(uti)
    )
}

private fun PHAsset.getAssetOriginalFileName(): String? {
    val resources = PHAssetResource.assetResourcesForAsset(this)
    if (resources.isEmpty())
        return null

    val resource = resources.first()

    return resource.originalFilename()
}

private fun <R> withAssetData(asset: PHAsset, body: (data: NSData?, dataUTI: String?) -> R): R {
    val manager = PHImageManager.defaultManager()

    val options = PHImageRequestOptions.alloc().init()
    options.isNetworkAccessAllowed = false
    //using sync here causes a segfault on the worker thread for whatever reason; seems to be some bug with MOE or
    //something since this works fine via swift
    //so we just hack around it for now since this works even though it's a waste of a worker thread
    //TODO check if this affects 1.3 as well
    //options.isSynchronous = true
    val d = deferred<Pair<NSData?, String?>, Exception>()

    manager.requestImageDataForAssetOptionsResultHandler(asset, options) { data, dataUTI, orientation, info ->
        d.resolve(data to dataUTI)
    }

    val pair = d.promise.get()

    return body(pair.first, pair.second)
}

private fun PHAsset.getFileInfo(): FileInfo {
    val originalFileName = this.getAssetOriginalFileName() ?: throw FileNotFoundException("No filename set")
    val fileInfo = withAssetData(this) { data, dataUTI ->
        if (data == null || dataUTI == null)
            null
        else {
            val mimeType = utiToMimeType(dataUTI)
            FileInfo(originalFileName, data.length(), mimeType)
        }
    } ?: throw FileNotFoundException("No data found for asset")

    return fileInfo
}

/**
 * Supported path types:
 *
 * bookmarks: Bookmarked security-scoped URL
 * assets: Assets library URL returned from image selection
 * files: File paths. Not actual valid URLs (nothing is encoded). Just used for cache files.
 */
class IOSFileAccess : PlatformFileAccess {
    companion object {
        const val BOOKMARK_SCHEMA = "bookmark://"

        const val ASSET_SCHEMA = "assets-library://"
    }

    private sealed class PathType {
        class Bookmark(val url: NSURL) : PathType()
        class Asset(val asset: PHAsset) : PathType()
        class File(val file: java.io.File) : PathType()
    }

    private fun resolveFilePath(path: String): PathType {
        return PathType.File(File(path))
    }

    private fun resolveBookmarkPath(path: String): PathType {
        val data = NSData.alloc().initWithBase64EncodedStringOptions(path.substring(BOOKMARK_SCHEMA.length), 0)
        if (data == null)
            throw IllegalArgumentException("Failed to deserialize bookmark data, invalid base64-encoded string")

        //TODO
        val isStale = PtrFactory.newBoolReference()
        val errPtr = newNSErrorPtr()
        val url = NSURL.URLByResolvingBookmarkDataOptionsRelativeToURLBookmarkDataIsStaleError(
            data,
            0,
            null,
            isStale,
            errPtr
        )

        checkNSError(errPtr, "NSURL.URLByResolvingBookmarkData")

        return PathType.Bookmark(url)
    }

    private fun resolveAssetPath(path: String): PathType {
        val url = NSURL.URLWithString(path) ?: throw IllegalArgumentException("Invalid url format")

        val result = PHAsset.fetchAssetsWithALAssetURLsOptions(nsarray(url), null)
        if (result.count() <= 0) {
            throw FileNotFoundException("No asset found for url")
        }

        val asset = result.firstObject()

        return PathType.Asset(asset)
    }

    private fun parsePath(path: String): PathType {
        return if (path.startsWith(BOOKMARK_SCHEMA)) {
            resolveBookmarkPath(path)
        }
        else if (path.startsWith(ASSET_SCHEMA)) {
            resolveAssetPath(path)
        }
        else  {
            resolveFilePath(path)
        }
    }

    private fun getFilePathInfo(file: File): FileInfo {
        return FileInfo(
            file.name,
            file.length(),
            //just used for cache files so whatever
            "*/*"
        )
    }

    private fun getBookmarkInfo(url: NSURL): FileInfo {
        return url.access {
            url.getFileInfo()
        }
    }

    private fun getAssetInfo(asset: PHAsset): FileInfo {
        return asset.getFileInfo()
    }

    override fun getFileInfo(path: String): FileInfo {
        val p = parsePath(path)

        return when (p) {
            is PathType.Bookmark -> getBookmarkInfo(p.url)
            is PathType.Asset -> getAssetInfo(p.asset)
            is PathType.File -> getFilePathInfo(p.file)
        }
    }

    override fun openFileForRead(path: String, body: (InputStream) -> Unit) {
        val p = parsePath(path)

        return when (p) {
            is PathType.Bookmark -> doBookmarkRead(p.url, body)
            is PathType.Asset -> doAssetRead(p.asset, body)
            is PathType.File -> TODO()
        }
    }

    private fun doAssetRead(asset: PHAsset, body: (InputStream) -> Unit) {
        val data = withAssetData(asset) { data, _ ->
            data
        } ?: throw FileNotFoundException("No data for asset")

        return body(NSDataInputStream(data))
    }

    private fun doBookmarkRead(url: NSURL, body: (InputStream) -> Unit) {
        url.access {
            url.openForReadWithCoordinator {
                NSFileHandleInputStream(it).use(body)
            }
        }
    }

    override fun openFileForWrite(path: String): OutputStream {
        val p = parsePath(path)

        val inputStream = when (p) {
            is PathType.File -> FileOutputStream(p.file)
            else -> TODO()
        }

        return inputStream
    }

    override fun delete(path: String) {
        TODO()
    }
}

