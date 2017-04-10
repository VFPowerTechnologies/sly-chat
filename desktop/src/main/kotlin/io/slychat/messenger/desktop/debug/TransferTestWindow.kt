package io.slychat.messenger.desktop.debug

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.div
import io.slychat.messenger.core.enforceExhaustive
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.rx.plusAssign
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.files.*
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.collections.FXCollections
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
import java.util.concurrent.Callable
import kotlin.reflect.KProperty

private operator fun <T> Property<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

private operator fun <T> Property<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
    return value
}

//model item used by tableview
@Suppress("HasPlatformType")
class TransferItem(var transfer: Transfer, var realState: TransferState) {
    val typeProperty = SimpleStringProperty()
    var type by typeProperty

    val idProperty = SimpleStringProperty()
    var id by idProperty

    val progressProperty = SimpleDoubleProperty(0.0)
    var progress by progressProperty

    val stateProperty = SimpleStringProperty()
    var state by stateProperty

    val errorProperty = SimpleStringProperty()
    var error by errorProperty

    val untilRetryProperty = SimpleLongProperty(-1)
    var untilRetry by untilRetryProperty

    init {
        updateTransferValues()
        updateStateValues()
    }

    private fun updateTransferValues() {
        val transfer = this.transfer
        type = when (transfer) {
            is Transfer.U -> "U"
            is Transfer.D -> "D"
        }
        id = transfer.id
        @Suppress("IMPLICIT_CAST_TO_ANY")
        error = when (transfer) {
            is Transfer.U -> transfer.upload.error
            is Transfer.D -> transfer.download.error
        }?.toString() ?: "-"
    }

    private fun updateStateValues() {
        this.state = realState.toString()
    }

    fun update(progress: TransferProgress) {
        this.progress = progress.transferedBytes.toDouble() / progress.totalBytes
    }

    fun update(state: TransferState) {
        this.realState = state
        updateStateValues()
    }

    fun update(transfer: Transfer) {
        this.transfer = transfer
        updateTransferValues()
    }
}

class TransferTestWindow(mainStage: Stage, app: SlyApplication) : Stage() {
    private val log = LoggerFactory.getLogger(javaClass)

    private val singlePartUploadBtn: Button
    private val multiPartUploadBtn: Button
    private val customUploadBtn: Button

    private val doCacheCheckBox: CheckBox

    private val transferTableView: TableView<TransferItem>
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

    private val currentTransfers = FXCollections.observableArrayList<TransferItem>()

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

        transferTableView = TableView(currentTransfers)
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

    private fun getSelectedStatuses(): List<Transfer> {
        return transferTableView.selectionModel.selectedItems.map { it.transfer }
    }

    private fun getSelectedStatus(): Transfer {
        return transferTableView.selectionModel.selectedItem.transfer
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
            uc.storageService.cancel(getSelectedStatuses().map { it.id })
        }

        activeTransferContextMenu.items.addAll(cancel)

        val retry = MenuItem("Retry")
        retry.setOnAction {
            val status = getSelectedStatus()
            uc.storageService.retry(status.id)
        }

        val errorCancel = MenuItem("Cancel")
        errorCancel.setOnAction {
            uc.storageService.cancel(getSelectedStatuses().map { it.id })
        }

        errorTransferContextMenu.items.addAll(retry, errorCancel)

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
            TransferState.CANCELLING -> null
        }
    }

    private fun initTransferTable() {
        val typeCol = TableColumn<TransferItem, String>("Type")
        typeCol.setCellValueFactory {
            it.value.typeProperty
        }

        val idCol = TableColumn<TransferItem, String>("ID")
        idCol.setCellValueFactory {
            it.value.idProperty
        }
        idCol.prefWidth = 250.0

        val progressCol = TableColumn<TransferItem, Double>("Progress")
        progressCol.setCellValueFactory {
            it.value.progressProperty.asObject()
        }

        progressCol.cellFactory = ProgressBarTableCell.forTableColumn()

        val stateCol = TableColumn<TransferItem, String>("State")
        stateCol.setCellValueFactory { it.value.stateProperty }
        stateCol.prefWidth = 110.0

        val errorCol = TableColumn<TransferItem, String>("Error")
        errorCol.setCellValueFactory {
            it.value.errorProperty
        }
        errorCol.prefWidth = 180.0

        val retryCol = TableColumn<TransferItem, String>("Retry in")
        retryCol.setCellValueFactory {
            //XXX this is stupid
            Bindings.createStringBinding(Callable {
                val s = it.value.untilRetry

                if (s.toLong() > 0)
                   "${it.value.untilRetry}s"
                else
                    ""
            }, it.value.untilRetryProperty)
        }

        transferTableView.columns.addAll(typeCol, idCol, progressCol, stateCol, errorCol, retryCol)

        val tableContextMenu = ContextMenu()
        val clearComplete = MenuItem("Clear complete")
        clearComplete.setOnAction { uc.storageService.removeCompleted() }
        tableContextMenu.items.addAll(clearComplete)

        transferTableView.setRowFactory {
            val row = TableRow<TransferItem>()

            row.setOnContextMenuRequested { ev ->
                val item = row.item

                val contextMenu = if (item != null)
                    getTransferContextMenu(item.realState)
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
            val item = TransferItem(it.transfer, it.state)
            item.update(it.progress)
            currentTransfers.add(item)
        }
    }

    private fun clearTransfers() {
        currentTransfers.clear()
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
        @Suppress("IMPLICIT_CAST_TO_ANY")
        when (ev) {
            is TransferEvent.Added -> {
                currentTransfers.add(TransferItem(ev.transfer, ev.state))
            }

            is TransferEvent.StateChanged -> {
                val item = currentTransfers.find { it.transfer.id == ev.transfer.id }
                item?.apply {
                    update(ev.state)
                    //error might be reset here
                    update(ev.transfer)
                }
            }

            is TransferEvent.Progress -> {
                val item = currentTransfers.find { it.transfer.id == ev.transfer.id }
                item?.update(ev.progress)
            }

            is TransferEvent.Removed -> {
                val removedIds = ev.transfers.map { it.id }.toSet()
                currentTransfers.removeAll { it.transfer.id in removedIds }
            }

            is TransferEvent.UntilRetry -> {
                val item = currentTransfers.find { it.transfer.id == ev.transfer.id }
                item?.untilRetry = ev.remainingSecs
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