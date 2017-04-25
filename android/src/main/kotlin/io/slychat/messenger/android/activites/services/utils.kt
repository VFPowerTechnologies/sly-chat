@file:JvmName("AndroidServiceUtils")
package io.slychat.messenger.android.activites.services

import android.content.Context
import io.slychat.messenger.android.R
import io.slychat.messenger.core.persistence.DirEntry
import io.slychat.messenger.services.files.TransferStatus
import java.math.BigDecimal

fun getFileTypeIcon(context: Context, fileName: String): Int {
    var ext = ""
    val i = fileName.lastIndexOf('.')
    if (i > 0) {
        ext = fileName.substring(i + 1)
    }

    var id = context.resources.getIdentifier(ext, "drawable", context.packageName)

    if (id <= 0)
        id = R.drawable.unknown

    return id
}

fun List<TransferStatus>.toAndroid(): List<AndroidTransferStatus> {
    val transfers = mutableListOf<AndroidTransferStatus>()
    this.forEach { t ->
        transfers.add(AndroidTransferStatus(t.transfer, t.file, t.state, t.progress, null))
    }

    return transfers
}

fun List<DirEntry>.toAndroidDirEntry(): List<AndroidDirEntry> {
    val entries = mutableListOf<AndroidDirEntry>()

    this.forEach {
        when (it) {
            is DirEntry.D -> entries.add(AndroidDirEntry.D(it.fullPath, it.name))
            is DirEntry.F -> entries.add(AndroidDirEntry.F(it.file))
        }
    }

    return entries
}

fun getHumanReadableFileSize(size: Float?): String {
    size ?: return ""

    val kb = 1024F
    val mb = kb * 1024F
    val gb = mb * 1024F

    return if (size < kb)
        "${Math.round(size)} bytes"
    else if (size >= kb && size < mb)
        "${round(size / kb, 2)} kb"
    else if (size >= mb && size < gb)
        "${round(size / mb, 2)} mb"
    else if (size >= gb)
        "${round(size / mb, 2)} gb"
    else
        ""
}

fun round(d: Float, decimalPlace: Int): Float {
    var bd = BigDecimal(d.toString())
    bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP)
    return bd.toFloat()
}