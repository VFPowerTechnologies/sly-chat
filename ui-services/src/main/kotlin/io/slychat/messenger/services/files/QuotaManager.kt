package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import rx.Observable

interface QuotaManager {
    val quota: Observable<Quota>

    fun update(quota: Quota)
}