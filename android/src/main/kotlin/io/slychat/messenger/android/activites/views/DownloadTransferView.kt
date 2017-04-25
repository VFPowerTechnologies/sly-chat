package io.slychat.messenger.android.activites.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.AndroidTransferStatus
import io.slychat.messenger.services.files.TransferState

class DownloadTransferView(
        transferStatus: AndroidTransferStatus?,
        context: Context,
        attrs: AttributeSet?) : LinearLayout(context, attrs) {

    constructor(context: Context, attrs: AttributeSet?): this(null, context, attrs)

    private val mTransferFileName: TextView
    private val mTransferStatus: TextView
    private val mTransferDate: TextView
    private val mTransferRetry: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.download_transfer, this, true)

        mTransferFileName = findViewById(R.id.transfer_file_name) as TextView
        mTransferStatus = findViewById(R.id.transfer_status) as TextView
        mTransferDate = findViewById(R.id.transfer_date) as TextView
        mTransferRetry = findViewById(R.id.transfer_retry) as TextView

        if (transferStatus != null)
            setTransferInfo(transferStatus)
    }

    private fun setTransferInfo(transferStatus: AndroidTransferStatus) {
        mTransferFileName.text = getFileNameFromRemoteName(transferStatus.transfer.remoteDisplayName)

        when(transferStatus.state) {
            TransferState.ACTIVE -> {
                val prog = ((transferStatus.progress.transferedBytes.toFloat() / transferStatus.progress.totalBytes.toFloat()) * 100).toLong().toString() + "%"
                mTransferStatus.text = prog
            }
            TransferState.ERROR -> {
                mTransferStatus.text = context.resources.getString(R.string.generic_error)
                if (transferStatus.untilRetry != null) {
                    val text = transferStatus.untilRetry.toString() + resources.getString(R.string.transfer_retry_delay)
                    mTransferRetry.visibility = View.VISIBLE
                    mTransferRetry.text = text
                }
            }
            TransferState.COMPLETE -> {
                mTransferStatus.text = context.resources.getString(R.string.transfer_complete)
            }
            TransferState.CANCELLED -> {
                mTransferStatus.text = context.resources.getString(R.string.transfer_cancelled)
            }
            TransferState.CANCELLING -> {
                mTransferStatus.text = context.resources.getString(R.string.transfer_cancelling)
            }
            TransferState.QUEUED -> {
                mTransferStatus.text = context.resources.getString(R.string.transfer_queued)
            }
        }
    }

    private fun getFileNameFromRemoteName(remoteFileName: String): String {
        return remoteFileName.split("/").last()
    }
}