package io.slychat.messenger.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.slychat.messenger.services.files.cache.ThumbnailGenerator
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

//converted to kotlin from android docs
private fun calcSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var sampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        //calculate the largest sample size that's a power of 2 and keeps both height and width larger than the requested
        //height and width
        while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth)
            sampleSize *= 2
    }

    return sampleSize
}

private fun readSubsampled(inputStream: InputStream, reqWidth: Int, reqHeight: Int): Bitmap? {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true

    val buffered = BufferedInputStream(inputStream)
    buffered.mark(1024)

    BitmapFactory.decodeStream(buffered, null, options)

    buffered.reset()

    println("${options.outWidth}x${options.outHeight} image (mimeType=${options.outMimeType})")

    options.inSampleSize = calcSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
    options.inJustDecodeBounds = false

    println("Using sample size = ${options.inSampleSize}")

    return BitmapFactory.decodeStream(buffered, null, options)
}

private fun writeThumbnailStream(bitmap: Bitmap, outputStream: OutputStream) {
    bitmap.compress(Bitmap.CompressFormat.WEBP, 75, outputStream)
}

class AndroidThumbnailGenerator : ThumbnailGenerator {
    override fun generateThumbnail(originalInputStream: InputStream, thumbnailOutputStream: OutputStream, thumbnailResolution: Int): Promise<Unit, Exception> = task {
        val bitmap = readSubsampled(originalInputStream, thumbnailResolution, thumbnailResolution) ?:
            throw RuntimeException("Image in unrecognized format, unable to decode")

        writeThumbnailStream(bitmap, thumbnailOutputStream)
    }
}