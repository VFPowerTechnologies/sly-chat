package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.VersionCheckResult
import io.slychat.messenger.services.ui.UIClientInfoService
import rx.Observable
import java.util.*

class UIClientInfoServiceImpl(
    versionOutOfDate: Observable<VersionCheckResult>
) : UIClientInfoService {
    private var lastResult: VersionCheckResult? = null
    private val versionOutOfDateListeners = ArrayList<(VersionCheckResult) -> Unit>()

    init {
        versionOutOfDate.subscribe { onVersionCheck(it) }
    }

    override var isFirstRun: Boolean = false

    private fun onVersionCheck(result: VersionCheckResult) {
        lastResult = result
        versionOutOfDateListeners.forEach { it(result) }
    }

    override fun addVersionOutdatedListener(listener: (VersionCheckResult) -> Unit) {
        versionOutOfDateListeners.add(listener)

        lastResult?.apply { listener(this) }
    }

    override fun clearListeners() {
        versionOutOfDateListeners.clear()
    }
}