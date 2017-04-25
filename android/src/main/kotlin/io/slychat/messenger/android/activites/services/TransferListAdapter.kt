package io.slychat.messenger.android.activites.services

import android.widget.ArrayAdapter
import android.view.ViewGroup
import android.content.Context
import android.view.View
import io.slychat.messenger.android.activites.ManageTransferActivity
import io.slychat.messenger.android.activites.views.DownloadTransferView
import io.slychat.messenger.android.activites.views.UploadTransferView
import io.slychat.messenger.services.files.Transfer
import io.slychat.messenger.services.files.TransferEvent

class TransferListAdapter(context: Context, private val values: List<AndroidTransferStatus>, val manageTransferActivity: ManageTransferActivity) : ArrayAdapter<AndroidTransferStatus>(context, -1, values) {

    val mapData = mutableMapOf<String, Int>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val transferStatus = values[position]

        val view: View
        when (transferStatus.transfer) {
            is Transfer.D -> {
                view = DownloadTransferView(transferStatus, context, null)
            }
            is Transfer.U -> {
                view = UploadTransferView(transferStatus, manageTransferActivity, null)
            }
        }

        return view
    }

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }

    override fun remove(`object`: AndroidTransferStatus?) {
        super.remove(`object`)
        updateMap()
    }

    private fun updateMap() {
        for((index, element) in values.withIndex()) {
            mapData.put(element.id, index)
        }
    }

    fun updateProgress(event: TransferEvent.Progress) {
        val position = mapData[event.transfer.id]
        position ?: return

        val item = getItem(position)
        item.progress = event.progress
        notifyDataSetChanged()
    }

    fun updateState(event: TransferEvent.StateChanged) {
        val position = mapData[event.transfer.id]
        position ?: return

        val item = getItem(position)
        item.state = event.state
        notifyDataSetChanged()
    }

    fun addAll(files: List<AndroidTransferStatus>) {
        super.addAll(files)
        updateMap()
    }

    override fun add(`object`: AndroidTransferStatus) {
        super.add(`object`)
        updateMap()
    }

    fun get(position: Int): AndroidTransferStatus? {
        return values[position]
    }

    fun removeTransfers(transfers: List<Transfer>) {
        transfers.forEach { transfer ->
            removeTransfer(transfer.id)
        }
    }

    fun removeTransfer(id: String) {
        val position = mapData[id]
        position ?: return

        remove(values[position])
    }

    fun updateRetryStatus(event: TransferEvent.UntilRetry) {
        val position = mapData[event.transfer.id]
        position ?: return

        get(position)?.untilRetry = event.remainingSecs
        notifyDataSetChanged()
    }
}