package io.slychat.messenger.services

import rx.Observable

interface TokenFetchService {
    val tokenUpdates: Observable<String>

    fun refresh()
}