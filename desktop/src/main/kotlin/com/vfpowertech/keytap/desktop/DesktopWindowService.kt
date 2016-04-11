package com.vfpowertech.keytap.desktop

import com.vfpowertech.keytap.services.ui.UIWindowService
import javafx.stage.Stage

class DesktopWindowService(private val stage: Stage) : UIWindowService {
    override fun minimize() {
        stage.isIconified = true
    }

    override fun closeSoftKeyboard() = Unit

}