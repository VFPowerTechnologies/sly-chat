package io.slychat.messenger.android

import android.content.Context
import android.graphics.Path
import android.net.Uri
import android.provider.OpenableColumns
import io.slychat.messenger.services.files.FileInfo
import io.slychat.messenger.services.files.PlatformFileAccess
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.*

class AndroidFileAccess(private val context: Context) : PlatformFileAccess {
    private sealed class PathType {
        class F(val file: File) : PathType()
        class U(val uri: Uri) : PathType()
    }

    private fun getFileUriInfo(file: File): FileInfo {
        return FileInfo(
            file.name,
            file.length(),
            //just used for cache files so whatever
            "*/*"
        )
    }

    private fun getContentUriInfo(uri: Uri): FileInfo {
        return try {
            val mimeType = context.contentResolver.getType(uri)

            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null).use { cursor ->
                if (cursor == null)
                    throw FileNotFoundException()

                val displayNameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)

                //don't think this can happen since these cols are required
                require(displayNameIdx < 0 || sizeIdx < 0) { "Unable to resolve col indexes: displayNameIdx=$displayNameIdx; sizeIdx=$sizeIdx" }

                if (!cursor.moveToFirst())
                    throw FileNotFoundException()

                val displayName = cursor.getString(displayNameIdx)
                val size = cursor.getLong(sizeIdx)

                FileInfo(displayName, size, mimeType)
            }
        }
        catch (e: SecurityException) {
            throw FileNotFoundException()
        }
    }

    private fun parsePath(path: String): PathType {
        return if (path.startsWith("content://"))
            PathType.U(Uri.parse(path))
        else
            PathType.F(File(path))
    }

    private fun queryFileInfo(path: String): FileInfo {
        val p = parsePath(path)
        return when (p) {
            is PathType.F -> getFileUriInfo(p.file)
            is PathType.U -> getContentUriInfo(p.uri)
        }
    }

    override fun getFileSize(path: String): Promise<Long, Exception> {
        return task { queryFileInfo(path).size }
    }

    override fun getFileDisplayName(path: String): Promise<String, Exception> {
        return task { queryFileInfo(path).displayName }
    }

    override fun getMimeType(path: String): Promise<String, Exception> {
        return task { queryFileInfo(path).mimeType }
    }

    override fun getFileInfo(path: String): Promise<FileInfo, Exception> {
        return task { queryFileInfo(path) }
    }

    override fun openFileForRead(path: String): Promise<InputStream, Exception> {
        return task {
            val p = parsePath(path)

            when (p) {
                is PathType.F -> FileInputStream(p.file)
                is PathType.U -> context.contentResolver.openInputStream(p.uri)
            }
        }
    }

    override fun openFileForWrite(path: String): Promise<OutputStream, Exception> {
        return task {
            val p = parsePath(path)

            when (p) {
                is PathType.F -> FileOutputStream(p.file)
                is PathType.U -> context.contentResolver.openOutputStream(p.uri)
            }
        }
    }
}