package com.vfpowertech.keytap.desktop

import com.vfpowertech.keytap.services.ui.UIWindowService
import javafx.stage.Stage
import nl.komponents.kovenant.Promise

class DesktopWindowService(private val stage: Stage) : UIWindowService {
    override fun minimize() {
        stage.isIconified = true
    }

    override fun closeSoftKeyboard(): Promise<Unit, Exception> = Promise.ofSuccess(Unit)

}