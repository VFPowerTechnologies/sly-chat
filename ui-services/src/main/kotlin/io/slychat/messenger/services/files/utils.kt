@file:JvmName("FilesUtils")
package io.slychat.messenger.services.files

import io.slychat.messenger.core.UnauthorizedException
import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.mb
import io.slychat.messenger.core.persistence.UploadPart
import io.slychat.messenger.services.auth.AuthTokenManager
import rx.Observable
import java.util.*
import java.util.concurrent.TimeUnit

//S3 limitation; each part except the last must be >= 5mb
internal val MIN_PART_SIZE = 5.mb

internal fun getRemoteFileSize(cipher: Cipher, fileSize: Long, chunkSize: Int): Long {
    require(fileSize > 0) { "fileSize must be > 0, got $fileSize" }
    require(chunkSize > 0) { "chunkSize must be > 0, got $chunkSize" }

    val chunkCount = fileSize / chunkSize
    val rem = fileSize % chunkSize
    val extra = if (rem > 0)
        cipher.getEncryptedSize(rem.toInt())
    else
        0

    val encryptedChunkSize = cipher.getEncryptedSize(chunkSize)

    val evenChunks = encryptedChunkSize * chunkCount

    return evenChunks + extra
}

/** Splits the given file into parts. File is assumed to be already encrypted. */
internal fun calcUploadPartsEncrypted(remoteFileSize: Long, minPartSize: Int): List<UploadPart> {
    val parts = ArrayList<UploadPart>()

    var remaining = remoteFileSize
    var offset = 0L
    var n = 1

    val mps = minPartSize.toLong()

    while (remaining > 0) {
        val partSize = Math.min(remaining, mps)

        parts.add(
            UploadPart(n, offset, partSize, partSize, false)
        )

        n += 1
        offset += partSize
        remaining -= partSize
    }

    return parts
}

/**
 * This will calculate the UploadParts for a given file.
 */
internal fun calcUploadParts(cipher: Cipher, localFileSize: Long, chunkSize: Int, minPartSize: Int): List<UploadPart> {
    require(chunkSize <= minPartSize) { "encryptedChunkSize ($chunkSize) should be <= minPartSize ($minPartSize)" }
    require((minPartSize % chunkSize) == 0) { "encryptedChunkSize ($chunkSize) must be a multiple of minPartSize ($minPartSize)" }

    val encryptedChunkSize = cipher.getEncryptedSize(chunkSize)

    val chunksPerPart = (minPartSize / chunkSize).toLong()

    if (localFileSize < chunkSize) {
        return listOf(UploadPart(
            1,
            0,
            localFileSize,
            cipher.getEncryptedSize(localFileSize.toInt()).toLong(),
            false
        ))
    }

    val evenChunks = localFileSize / chunkSize
    //if > 0, then this is the bytes that make up the final chunk
    val lastChunkSize = localFileSize % chunkSize

    val evenParts = (evenChunks / chunksPerPart).toInt()
    //remaining chunks (plus one more if lastChunkSize is > 1) for the final part
    val pr = evenChunks % chunksPerPart

    val parts = ArrayList<UploadPart>()
    var currentOffset = 0L

    (1..evenParts).forEach {
        val p = UploadPart(it, currentOffset, chunksPerPart * chunkSize, chunksPerPart * encryptedChunkSize, false)
        currentOffset += p.localSize
        parts.add(p)
    }

    if (pr > 0 || lastChunkSize > 0) {
        val lastChunkEncryptedSize = if (lastChunkSize > 0)
            cipher.getEncryptedSize(lastChunkSize.toInt())
        else
            0

        parts.add(UploadPart(evenParts + 1, currentOffset, (pr * chunkSize) + lastChunkSize, (pr * encryptedChunkSize) + lastChunkEncryptedSize, false))
    }

    return parts
}

internal fun <T> authFailureRetry(authTokenManager: AuthTokenManager, observable: Observable<T>): Observable<T> {
    return observable.retryWhen {
        val maxRetries = 3
        it.zipWith(Observable.range(1, maxRetries + 1), { e, i -> e to i }).flatMap {
            val (e, i) = it

            if (i > maxRetries)
                Observable.error(e)
            else {
                if (e is UnauthorizedException) {
                    authTokenManager.invalidateToken()
                    val exp = Math.pow(2.0, i.toDouble())
                    val secs = Random().nextInt(exp.toInt() + 1).toLong()
                    Observable.timer(secs, TimeUnit.SECONDS)
                }
                else
                    Observable.error(e)
            }
        }
    }
}
