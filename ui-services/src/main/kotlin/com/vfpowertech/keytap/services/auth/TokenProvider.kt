package com.vfpowertech.keytap.services.auth

import rx.Observable

//job is to handle fetching auth tokens, and refreshing expired tokens
//a token provider should be properly configured to timeout, as tasks that request tokens are queued until a token is available or an error occurs
interface TokenProvider {
    //must occur on the main thread?
    val events: Observable<TokenEvent>

    //should fetch another token if possible
    fun invalidateToken()
}


