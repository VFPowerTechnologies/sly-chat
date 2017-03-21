package io.slychat.messenger.desktop.debug

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.div
import io.slychat.messenger.core.enforceExhaustive
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.files.*
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.ProgressBarTableCell
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.subscriptions.CompositeSubscription
import java.io.File

class TransferTestWindow(mainStage: Stage, app: SlyApplication) : Stage() {
    private val log = LoggerFactory.getLogger(javaClass)

    private val singlePartUploadBtn: Button
    private val multiPartUploadBtn: Button
    private val customUploadBtn: Button

    private val doCacheCheckBox: CheckBox

    private val transferTableView: TableView<TransferStatus>
    private val fileTableView: TableView<RemoteFile>

    private val subscriptions = CompositeSubscription()
    private val userSubscriptions = CompositeSubscription()
    private var userComponent: UserComponent? = null
    private val uc: UserComponent
        get() = userComponent ?: error("Not logged in")

    private val fileItemContextMenu = ContextMenu()

    private val activeTransferContextMenu = ContextMenu()
    private val completeTransferContextMenu = ContextMenu()
    private val queuedTransferContextMenu = ContextMenu()
    private val errorTransferContextMenu = ContextMenu()
    private val cancelledTransferContextMenu = ContextMenu()

    private val quotaLabel = Label("0/0")
    //kinda hacky but w/e
    private val quotaBar = ProgressBar()

    init {
        val root = VBox()

        initOwner(mainStage)

        singlePartUploadBtn = Button("Test single part upload")
        singlePartUploadBtn.isDisable = true
        singlePartUploadBtn.maxWidth = Double.MAX_VALUE
        root.children.add(singlePartUploadBtn)

        singlePartUploadBtn.setOnAction { onSinglePartUploadClicked() }

        multiPartUploadBtn = Button("Test multi part upload")
        multiPartUploadBtn.isDisable = true
        multiPartUploadBtn.maxWidth = Double.MAX_VALUE
        multiPartUploadBtn.setOnAction { onMultiPartUploadClicked() }
        root.children.add(multiPartUploadBtn)

        customUploadBtn = Button("Upload custom file")
        customUploadBtn.isDisable = true
        customUploadBtn.maxWidth = Double.MAX_VALUE
        customUploadBtn.setOnAction { onCustomFileUploadClicked() }
        root.children.add(customUploadBtn)

        doCacheCheckBox = CheckBox("Cache file")
        doCacheCheckBox.isSelected = false
        root.children.add(doCacheCheckBox)

        val quotaBox = HBox()
        quotaBar.progress = 0.0
        quotaBar.maxWidth = Double.MAX_VALUE
        HBox.setHgrow(quotaBar, Priority.ALWAYS)
        quotaBox.children.addAll(quotaBar, quotaLabel)

        root.children.add(quotaBox)

        initFilesContextMenu()

        initTransfersContextMenu()

        transferTableView = TableView()
        initTransferTable()
        root.children.add(transferTableView)

        fileTableView = TableView()
        initFileTableView()
        root.children.add(fileTableView)
        VBox.setVgrow(fileTableView, Priority.ALWAYS)

        scene = Scene(root, 960.0, 490.0)

        setOnHidden {
            subscriptions.clear()
            userSubscriptions.clear()
        }

        subscriptions += app.userSessionAvailable.subscribe {
            onUserSessionAvailable(it)
        }
    }

    private fun getSelectedStatuses(): List<TransferStatus> {
        return transferTableView.selectionModel.selectedItems
    }

    private fun getSelectedStatus(): TransferStatus {
        return transferTableView.selectionModel.selectedItem
    }

    private fun onTransferRemoveSelected() {
        val statuses = getSelectedStatuses()

        uc.storageService.remove(statuses.map { it.id }) fail {
            log.error("Failed to remove transfer: {}", it.message, it)
        }
    }

    private fun initTransfersContextMenu() {
        val completeRemove = MenuItem("Remove")
        completeRemove.setOnAction { onTransferRemoveSelected() }

        completeTransferContextMenu.items.addAll(completeRemove)

        val queuedRemove = MenuItem("Remove")
        queuedRemove.setOnAction { onTransferRemoveSelected() }

        queuedTransferContextMenu.items.add(queuedRemove)

        val cancel = MenuItem("Cancel")
        cancel.setOnAction {
            //TODO currently doesn't work with cancelled uploads
            uc.storageService.cancel(getSelectedStatuses().map { it.id })
        }

        activeTransferContextMenu.items.addAll(cancel)

        val retry = MenuItem("Retry")
        retry.setOnAction {
            val status = getSelectedStatus()
            uc.storageService.retry(status.id)
        }

        val errorRemove = MenuItem("Remove")
        errorRemove.setOnAction { onTransferRemoveSelected() }

        errorTransferContextMenu.items.addAll(retry, errorRemove)

        val cancelRemove = MenuItem("Remove")
        cancelRemove.setOnAction { onTransferRemoveSelected() }

        cancelledTransferContextMenu.items.addAll(cancelRemove)
    }

    private fun initFilesContextMenu() {
        val delete = MenuItem("Delete")
        delete.setOnAction {
            deleteFile(fileTableView.selectionModel.selectedItems)
        }

        val download = MenuItem("Download")
        download.setOnAction {
            downloadFile(fileTableView.selectionModel.selectedItems)
        }

        fileItemContextMenu.items.addAll(download, delete)
    }

    private fun downloadFile(files: List<RemoteFile>) {
        println("Queuing download for $files")
        val downloadDir = File(System.getProperty("java.io.tmpdir"))

        files.forEach {
            val path = downloadDir / it.userMetadata.fileName
            uc.storageService.downloadFile(it.id, path.path) fail {
                log.error("Failed to start download")
            }
        }
    }

    private fun initFileTableView() {
        val idCol = TableColumn<RemoteFile, String>("ID")
        idCol.cellValueFactory = PropertyValueFactory("id")
        idCol.prefWidth = 250.0

        val pathCol = TableColumn<RemoteFile, String>("Path")
        pathCol.setCellValueFactory {
            val file = it.value
            ReadOnlyObjectWrapper(file.userMetadata.directory + "/" + file.userMetadata.fileName)
        }
        pathCol.prefWidth = 200.0

        val sizeCol = TableColumn<RemoteFile, String>("Size")
        sizeCol.setCellValueFactory {
            ReadOnlyObjectWrapper(it.value.remoteFileSize.toString())
        }

        val isPendingCol = TableColumn<RemoteFile, String>("Pending")
        isPendingCol.setCellValueFactory {
            ReadOnlyObjectWrapper(it.value.isPending.toString())
        }

        fileTableView.columns.addAll(idCol, pathCol, sizeCol, isPendingCol)

        fileTableView.setRowFactory { tableView ->
            val row = TableRow<RemoteFile>()

            row.setOnContextMenuRequested { ev ->
                val file = row.item

                if (file != null) {
                    //pending files should not be modified, as they're tied to an upload
                    if (!file.isPending)
                        fileItemContextMenu.show(row, ev.screenX, ev.screenY)
                }
            }

            row
        }
    }

    private fun deleteFile(items: List<RemoteFile>) {
        uc.storageService.deleteFiles(items.map { it.id }) fail {
            log.error("Failed to delete items: {}", it.message, it)
        }
    }

    private fun getTransferContextMenu(transferState: TransferState): ContextMenu? {
        return when (transferState) {
            TransferState.COMPLETE -> completeTransferContextMenu
            TransferState.QUEUED -> queuedTransferContextMenu
            TransferState.ACTIVE -> activeTransferContextMenu
            TransferState.CANCELLED -> cancelledTransferContextMenu
            TransferState.ERROR -> errorTransferContextMenu
        }
    }

    private fun initTransferTable() {
        val typeCol = TableColumn<TransferStatus, String>("Type")
        typeCol.setCellValueFactory {
            ReadOnlyObjectWrapper(when (it.value.transfer) {
                is Transfer.U -> "U"
                is Transfer.D -> "D"
            })
        }

        val idCol = TableColumn<TransferStatus, String>("ID")
        idCol.setCellValueFactory {
            ReadOnlyObjectWrapper(it.value.id)
        }
        idCol.prefWidth = 250.0

        val progressCol = TableColumn<TransferStatus, Double>("Progress")
        progressCol.setCellValueFactory {
            val progress = it.value.progress
            ReadOnlyObjectWrapper(progress.transferedBytes.toDouble() / progress.totalBytes)
        }

        progressCol.setCellFactory {
            ProgressBarTableCell()
        }

        val stateCol = TableColumn<TransferStatus, String>("State")
        stateCol.setCellValueFactory { ReadOnlyObjectWrapper(it.value.state.toString()) }

        val errorCol = TableColumn<TransferStatus, String>("Error")
        errorCol.setCellValueFactory {
            val transfer = it.value.transfer
            ReadOnlyObjectWrapper(when (transfer) {
                is Transfer.U -> transfer.upload.error
                is Transfer.D -> transfer.download.error
            }?.toString() ?: "-")
        }
        errorCol.prefWidth = 180.0

        transferTableView.columns.addAll(typeCol, idCol, progressCol, stateCol, errorCol)

        val tableContextMenu = ContextMenu()
        val clearComplete = MenuItem("Clear complete")
        clearComplete.setOnAction { uc.storageService.removeCompleted() }
        tableContextMenu.items.addAll(clearComplete)

        transferTableView.setRowFactory {
            val row = TableRow<TransferStatus>()

            row.setOnContextMenuRequested { ev ->
                val status = row.item

                val contextMenu = if (status != null)
                    getTransferContextMenu(status.state)
                else
                    tableContextMenu

                contextMenu?.show(row, ev.screenX, ev.screenY)
            }

            row
        }
    }

    private fun clearQuota() {
        quotaLabel.text = "0/0"
        quotaBar.progress = 0.0
    }

    private fun onUserSessionAvailable(userComponent: UserComponent?) {
        this.userComponent = userComponent

        val disable = userComponent == null
        singlePartUploadBtn.isDisable = disable
        multiPartUploadBtn.isDisable = disable

        if (userComponent == null) {
            userSubscriptions.clear()
            clearTransfers()
            clearFiles()
            clearQuota()
        }
        else {
            userSubscriptions += userComponent.storageService.transferEvents.subscribe { onTransferEvent(it) }
            userSubscriptions += userComponent.storageService.syncEvents.subscribe { onStorageSyncEvent(it) }
            userSubscriptions += userComponent.storageService.quota.subscribe { onQuotaUpdate(it) }
            userSubscriptions += userComponent.storageService.fileEvents.subscribe { onFileEvent(it) }

            refreshTransfers()
            refreshFiles()
        }
    }

    private fun onFileEvent(ev: RemoteFileEvent) {
        when (ev) {
            is RemoteFileEvent.Added -> {
                println("Files added: ${ev.files}")
                refreshFiles()
            }

            is RemoteFileEvent.Deleted -> {
                println("Files deleted: ${ev.files}")
                refreshFiles()
            }

            is RemoteFileEvent.Updated -> {
                println("Files updated: ${ev.files}")
                refreshFiles()
            }
        }.enforceExhaustive()
    }

    private fun onQuotaUpdate(quota: Quota) {
        quotaLabel.text = "${quota.usedBytes}/${quota.maxBytes}"
        quotaBar.progress = quota.usedBytes / quota.maxBytes.toDouble()
    }

    private fun refreshFiles() {
        clearFiles()
        //TODO disable ui
        uc.storageService.getFiles(0, 1000) successUi {
            it.forEach {
                fileTableView.items.add(it)
            }
        } fail {
            log.error("Failed to fetch files: {}", it.message, it)
        }
    }

    private fun refreshTransfers() {
        clearTransfers()
        uc.storageService.transfers.forEach {
            transferTableView.items.add(it)
        }
    }

    private fun clearTransfers() {
        transferTableView.items.clear()
    }

    private fun clearFiles() {
        fileTableView.items.clear()
    }

    private fun enableUIInteraction(isEnabled: Boolean) {
        singlePartUploadBtn.isDisable = !isEnabled
        multiPartUploadBtn.isDisable = !isEnabled
        customUploadBtn.isDisable = !isEnabled
    }

    private fun onStorageSyncEvent(ev: FileListSyncEvent) {
        println("Storage sync event: $ev")

        when (ev) {
            is FileListSyncEvent.End -> {
                enableUIInteraction(true)
                //TODO this should return some kinda error reason eventually
                if (ev.hasError)
                    uc.storageService.clearSyncError()
            }

            is FileListSyncEvent.Begin -> {
                enableUIInteraction(false)
            }
        }
    }

    private fun onTransferEvent(ev: TransferEvent) {
        when (ev) {
            is TransferEvent.Added -> {
                refreshTransfers()
            }

            is TransferEvent.StateChanged -> {
                refreshTransfers()
            }

            is TransferEvent.Progress -> {
                refreshTransfers()
            }

            is TransferEvent.Removed -> {
                println("${ev.transfers} were removed")
                refreshTransfers()
            }
        }.enforceExhaustive()
    }

    private fun doUpload(fileName: String) {
        println("Starting upload")

        uc.storageService.uploadFile(
            (File(System.getProperty("user.home")) / "sly-dummy-files" / fileName).path,
            "/testing",
            fileName,
            doCacheCheckBox.isSelected
        ) fail {
            log.error("Failed to start upload: {}", it.message, it)
        }
    }

    private fun onMultiPartUploadClicked() {
        doUpload("multipart.bin")
    }

    private fun onSinglePartUploadClicked() {
        doUpload("singlepart.png")
    }

    private fun onCustomFileUploadClicked() {
        val file = FileChooser().apply {
            title = "Select file to upload"
            extensionFilters.add(FileChooser.ExtensionFilter("All Files", "*.*"))
            initialDirectory = File(System.getProperty("user.home"))
        }.showOpenDialog(this) ?: return

        uc.storageService.uploadFile(
            file.path,
            "/testing",
            file.name,
            doCacheCheckBox.isSelected
        ) fail {
            log.error("Failed to start upload: {}", it.message, it)
        }
    }
}