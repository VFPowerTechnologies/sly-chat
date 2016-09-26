package io.slychat.messenger.desktop

import io.slychat.messenger.services.config.SoundFilePath
import io.slychat.messenger.services.ui.UISelectionDialogResult
import io.slychat.messenger.services.ui.UIWindowService
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.FileChooser
import javafx.stage.Stage
import nl.komponents.kovenant.Promise
import java.io.File
import java.net.URI

class DesktopWindowService(private val stage: Stage) : UIWindowService {
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
        stage.isIconified = true
    }

    override fun closeSoftKeyboard() {}

    //this returns a URI (as a string); this is so we can use jar paths for default notifications (and testing)
    override fun selectNotificationSound(previous: SoundFilePath?): Promise<UISelectionDialogResult<SoundFilePath?>, Exception> {
        val fileChooser = FileChooser()

        fileChooser.title = "Message notification sound"

        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aac"))

        if (previous != null) {
            val file = File(URI(previous.uri).path)

            fileChooser.initialDirectory = file.parentFile
            //for some reason this doesn't work on linux; nfi why since gtk does support doing this
            fileChooser.initialFileName = file.name
        }

        val selectedFile = fileChooser.showOpenDialog(stage)

        //TODO allow silence somehow
        val (ok, value) = if (selectedFile == null) {
            false to null
        }
        else {
            val uri = selectedFile.toURI().toString()
            val name = selectedFile.name
            val displayName = if (name.contains('.'))
                name.substring(0, name.lastIndexOf('.'))
            else
                name

            true to SoundFilePath(displayName, uri)
        }

        return Promise.of(UISelectionDialogResult(ok, value))
    }
}