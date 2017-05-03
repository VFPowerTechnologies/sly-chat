package io.slychat.messenger.desktop

import io.slychat.messenger.services.ui.SoftKeyboardInfo
import io.slychat.messenger.services.ui.UISelectUploadFileResult
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.UIWindowService
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.FileChooser
import javafx.stage.Stage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File
import java.net.URI

class DesktopUIWindowService(
    var stage: Stage?,
    private val fileAccess: DesktopFileAccess
) : UIWindowService {
    override fun copyTextToClipboard(text: String) {
        val clipboard = Clipboard.getSystemClipboard()
        val content = ClipboardContent()
        content.putString(text)
        clipboard.setContent(content)
    }

    override fun getTextFromClipboard(): String? {
        val clipboard = Clipboard.getSystemClipboard()
        return clipboard.string
    }

    override fun minimize() {
        val stage = this.stage ?: return

        stage.isIconified = true
    }

    override fun closeSoftKeyboard() {}

    private fun handleFileChooserResult(selectedFile: File?): Promise<UISelectionDialogResult<String?>, Exception> {
        val (ok, value) = if (selectedFile == null)
            false to null
        else
            true to selectedFile.path

        return Promise.of(UISelectionDialogResult(ok, value))
    }

    //this returns a URI (as a string); this is so we can use jar paths for default notifications (and testing)
    override fun selectNotificationSound(previousUri: String?): Promise<UISelectionDialogResult<String?>, Exception> {
        val fileChooser = FileChooser()

        fileChooser.title = "Message notification sound"

        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aac"))

        if (previousUri != null) {
            val file = File(URI(previousUri).path)

            fileChooser.initialDirectory = file.parentFile
            //for some reason this doesn't work on linux; nfi why since gtk does support doing this
            fileChooser.initialFileName = file.name
        }

        //TODO allow silence somehow
        val selectedFile = fileChooser.showOpenDialog(stage)

        val (ok, value) = if (selectedFile == null)
            false to null
        else
            true to selectedFile.toURI().toString()

        return Promise.of(UISelectionDialogResult(ok, value))
    }

    override fun selectFileForUpload(): Promise<UISelectionDialogResult<UISelectUploadFileResult?>, Exception> {
        val file = FileChooser().apply {
            title = "Select file to upload"
            extensionFilters.add(FileChooser.ExtensionFilter("All Files", "*.*"))
            initialDirectory = getUserHome()
        }.showOpenDialog(stage)

        if (file == null)
            return Promise.of(UISelectionDialogResult(false, null))

        val path = file.path

        return task {
            val info = fileAccess.getFileInfo(path)

            UISelectionDialogResult(true, UISelectUploadFileResult(path, info))
        }
    }

    override fun selectSaveLocation(defaultFileName: String): Promise<UISelectionDialogResult<String?>, Exception> {
        return handleFileChooserResult(FileChooser().apply {
            title = "Select save location"
            initialDirectory = getUserHome()
            initialFileName = defaultFileName
        }.showSaveDialog(stage))
    }

    override fun setSoftKeyboardInfoListener(listener: (SoftKeyboardInfo) -> Unit) {}

    override fun clearListeners() {}
}