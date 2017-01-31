package io.slychat.messenger.services

import rx.Observable

interface TokenFetchService {
    val tokenUpdates: Observable<DeviceTokens?>

    fun refresh()
}