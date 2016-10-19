package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.ui.UIClientInfoService
import rx.Observable
import java.util.*

class UIClientInfoServiceImpl(
    versionOutOfDate: Observable<Unit>
) : UIClientInfoService {
    private var versionOutOfDate: Boolean = false
    private val versionOutOfDateListeners = ArrayList<() -> Unit>()

    init {
        versionOutOfDate.subscribe { onVersionOutOfDate() }
    }

    override var isFirstRun: Boolean = false

    private fun onVersionOutOfDate() {
        versionOutOfDate = true
        versionOutOfDateListeners.forEach { it() }
    }

    override fun addVersionOutdatedListener(listener: () -> Unit) {
        versionOutOfDateListeners.add(listener)

        if (versionOutOfDate)
            listener()
    }

    override fun clearListeners() {
        versionOutOfDateListeners.clear()
    }
}