package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Provides functionality for manipulating the native UI window. */
@JSToJavaGenerate("WindowService")
interface UIWindowService {
    fun minimize()

    fun closeSoftKeyboard()

    fun copyTextToClipboard(text: String)

    fun getTextFromClipboard(): String?

    fun selectNotificationSound(previousUri: String?): Promise<UISelectionDialogResult<String?>, Exception>

    fun selectFileForUpload(): Promise<UISelectionDialogResult<UISelectUploadFileResult?>, Exception>

    fun selectSaveLocation(defaultFileName: String): Promise<UISelectionDialogResult<String?>, Exception>

    fun setSoftKeyboardInfoListener(listener: (info: SoftKeyboardInfo) -> Unit)

    @Exclude
    fun clearListeners()
}