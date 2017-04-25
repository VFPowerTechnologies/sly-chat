package io.slychat.messenger.android.activites.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.getFileTypeIcon
import io.slychat.messenger.android.activites.services.getHumanReadableFileSize
import io.slychat.messenger.core.files.RemoteFile
import java.text.SimpleDateFormat
import java.util.*

class RemoteFileView(
        fileInfo: RemoteFile?,
        context: Context,
        attrs: AttributeSet?) : LinearLayout(context, attrs) {

    constructor(context: Context, attrs: AttributeSet?): this(null, context, attrs)

    private val mFileName: TextView
    private val mUploadDate: TextView
    private val mFileSize: TextView
    private val mFileId: TextView
    private val mFileIcon: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.remote_file, this, true)
        mFileName = findViewById(R.id.file_name) as TextView
        mUploadDate = findViewById(R.id.upload_date) as TextView
        mFileSize = findViewById(R.id.file_size) as TextView
        mFileId = findViewById(R.id.file_id) as TextView
        mFileIcon = findViewById(R.id.file_type_logo) as ImageView

        if (fileInfo != null)
            setFileInfo(fileInfo)
    }

    private fun setFileInfo(fileInfo: RemoteFile) {
        mFileName.text = fileInfo.userMetadata.fileName
        mFileSize.text = getHumanReadableFileSize(fileInfo.fileMetadata?.size?.toFloat())
        mUploadDate.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(fileInfo.creationDate*1000)
        mFileId.text = fileInfo.id

        val iconId = getFileTypeIcon(context, fileInfo.userMetadata.fileName)

        mFileIcon.setImageResource(iconId)

        if (fileInfo.isPending)
            mFileSize.text = resources.getString(R.string.file_transfer_pending)
    }
}