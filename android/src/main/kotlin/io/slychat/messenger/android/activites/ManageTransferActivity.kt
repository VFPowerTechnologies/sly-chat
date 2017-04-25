package io.slychat.messenger.android.activites

import android.app.AlertDialog
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.AndroidTransferStatus
import io.slychat.messenger.android.activites.services.TransferListAdapter
import io.slychat.messenger.android.activites.services.getHumanReadableFileSize
import io.slychat.messenger.android.activites.services.impl.AndroidStorageServiceImpl
import io.slychat.messenger.services.files.Transfer
import io.slychat.messenger.services.files.TransferEvent
import io.slychat.messenger.services.files.TransferState
import nl.komponents.kovenant.ui.failUi
import org.slf4j.LoggerFactory

class ManageTransferActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp
    private lateinit var androidStorageService: AndroidStorageServiceImpl

    private lateinit var mTransferList: ListView
    private lateinit var transferListAdapter: TransferListAdapter

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_manage_transfer)

        val actionBar = findViewById(R.id.manage_transfer_toolbar) as Toolbar
        actionBar.title = resources.getString(R.string.manage_transfer_title)
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        app = AndroidApp.get(this)
        androidStorageService = AndroidStorageServiceImpl(this)

        mTransferList = findViewById(R.id.transfer_list) as ListView
        registerForContextMenu(mTransferList)
        mTransferList.adapter = TransferListAdapter(this, mutableListOf(), this)

        setNavigationMenu()

        createEventListeners()
    }

    private fun init() {
        setListeners()
        fetchTransfers()
    }

    private fun fetchTransfers() {
        transferListAdapter = TransferListAdapter(baseContext, mutableListOf(), this)
        transferListAdapter.addAll(androidStorageService.getTransfers())
        mTransferList.adapter = transferListAdapter
    }

    private fun createEventListeners() {
    }

    private fun setListeners() {
        androidStorageService.addTransferListener { event ->
            handleTransferEvent(event)
        }
    }

    private fun clearListners() {
        androidStorageService.clearListeners()
    }

    private fun deleteTransfer(data: AndroidTransferStatus) {
        androidStorageService.remove(listOf(data.id)) failUi {
            log.error("Something failed {}", it.message, it)
        }
    }

    private fun retryTransfer(data: AndroidTransferStatus) {
        androidStorageService.retry(data.id) failUi {
            log.error("Something failed {}", it.message, it)
        }
    }

    private fun clearCompletedTransfers() {
        androidStorageService.removeCompleted() failUi {
            log.error("Something failed {}", it.message, it)
        }
    }

    private fun cancelTransfer(transferStatus: AndroidTransferStatus) {
        androidStorageService.cancel(listOf(transferStatus.id))
    }

    private fun handleTransferEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.Added -> { log.debug("New transfer added ${event.transfer.id}")}
            is TransferEvent.Removed -> { removeTransferFromList(event) }
            is TransferEvent.Progress -> { transferListAdapter.updateProgress(event) }
            is TransferEvent.StateChanged -> { transferListAdapter.updateState(event) }
            is TransferEvent.UntilRetry -> { transferListAdapter.updateRetryStatus(event) }
        }
    }

    private fun removeTransferFromList(event: TransferEvent.Removed) {
        transferListAdapter.removeTransfers(event.transfers)
    }

    private fun setNavigationMenu() {
        val navigationView = findViewById(R.id.manage_transfer_nav_view) as NavigationView
        navigationView.inflateMenu(R.menu.activity_manage_transfers_drawer)

        navigationView.setNavigationItemSelectedListener(this)

        val drawerName = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_name) as TextView
        val drawerEmail = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_email) as TextView

        drawerEmail.text = app.accountInfo?.email
        drawerName.text = app.accountInfo?.name
    }

    private fun showTransferDetails(data: AndroidTransferStatus) {
        val detailsLayout = LayoutInflater.from(this).inflate(R.layout.transfer_detais_dialog, null)
        val titleId: Int
        val fileName: String
        when (data.transfer) {
            is Transfer.D -> {
                titleId = R.id.transfer_details_title_download
                fileName = data.transfer.remoteDisplayName.split("/").last()
                val mRemotePathLayout = detailsLayout.findViewById(R.id.transfer_details_remote_path_layout)
                val mRemotePath = detailsLayout.findViewById(R.id.transfer_details_remote_file_path) as TextView
                mRemotePathLayout.visibility = View.VISIBLE
                mRemotePath.text = data.transfer.remoteDisplayName
            }
            is Transfer.U -> {
                titleId = R.id.transfer_details_title_upload
                fileName = data.transfer.localDisplayName
            }
        }

        val fileMetadata = data.file?.fileMetadata
        if (fileMetadata != null) {
            val mFileSizeLayout = detailsLayout.findViewById(R.id.transfer_details_file_size_layout)
            val mFileSize = detailsLayout.findViewById(R.id.transfer_details_file_size) as TextView
            mFileSizeLayout.visibility = View.VISIBLE
            mFileSize.text = getHumanReadableFileSize(fileMetadata.size.toFloat())
        }

        val mFileName = detailsLayout.findViewById(R.id.transfer_details_file_name) as TextView
        mFileName.text = fileName

        val mTitle = detailsLayout.findViewById(titleId)
        mTitle.visibility = View.VISIBLE

        val builder = AlertDialog.Builder(this)
        builder.setView(detailsLayout)
        builder.setNegativeButton(resources.getString(R.string.ok_button), { dialog, id ->
            dialog.dismiss()
        })

        dialog = builder.create()

        dialog?.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.layout_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_clear_completed -> { clearCompletedTransfers() }
        }

        val drawer = findViewById(R.id.manage_transfer_drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.END)
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)


        val inflater = menuInflater
        inflater.inflate(R.menu.transfer_context_menu, menu)

        val mDelete = menu.findItem(R.id.delete_transfer)
        val mCancel = menu.findItem(R.id.cancel_transfer)
        val mRetry = menu.findItem(R.id.retry_transfer)

        val info = menuInfo as AdapterView.AdapterContextMenuInfo
        val data = transferListAdapter.get(info.position)
        if (data != null) {
            when(data.state) {
                TransferState.ERROR -> {
                    mCancel.isVisible = true
                    mRetry.isVisible = true
                }
                TransferState.ACTIVE -> {
                    mCancel.isVisible = true
                }
                TransferState.CANCELLED -> {
                    mDelete.isVisible = true
                    mRetry.isVisible = true
                }
                TransferState.COMPLETE -> {
                    mDelete.isVisible = true
                }
                else -> {}
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val position = info.position
        val data = transferListAdapter.get(position)

        data ?: return false

        when (item.itemId) {
            R.id.retry_transfer -> { retryTransfer(data) }
            R.id.delete_transfer -> { deleteTransfer(data) }
            R.id.cancel_transfer -> { cancelTransfer(data) }
            R.id.transfer_details -> { showTransferDetails(data) }
        }

        return super.onContextItemSelected(item)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_menu -> {
                val drawer = findViewById(R.id.manage_transfer_drawer_layout) as DrawerLayout
                drawer.openDrawer(Gravity.END)
            }
            android.R.id.home -> { onBackPressed() }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        clearListners()
    }

    override fun onResume() {
        super.onResume()
        init()
    }

    override fun onStop() {
        super.onStop()
        clearListners()
    }
}