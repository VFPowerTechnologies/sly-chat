package io.slychat.messenger.ios

import apple.foundation.NSData
import apple.foundation.NSURL
import apple.photos.PHAsset
import io.slychat.messenger.services.files.FileInfo
import io.slychat.messenger.services.files.PlatformFileAccess
import org.moe.natj.general.ptr.impl.PtrFactory
import java.io.*

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

        val asset = url.fetchAsset() ?: throw FileNotFoundException("No asset found for url")

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

