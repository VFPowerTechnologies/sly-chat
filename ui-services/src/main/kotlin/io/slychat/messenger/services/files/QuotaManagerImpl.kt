package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.persistence.QuotaPersistenceManager
import nl.komponents.kovenant.task
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class QuotaManagerImpl(
    private val quotaPersistenceManager: QuotaPersistenceManager
) : QuotaManager {
    private val log = LoggerFactory.getLogger(javaClass)
    private val quotaSubject = BehaviorSubject.create<Quota>()

    override val quota: Observable<Quota>
        get() = quotaSubject

    override fun init() {
        val cached = quotaPersistenceManager.retrieve()
        if (cached != null)
            quotaSubject.onNext(cached)
    }

    override fun update(quota: Quota) {
        quotaSubject.onNext(quota)

        task {
            quotaPersistenceManager.store(quota)
        } fail {
            log.warn("Failed to store quota")
        }
    }
}