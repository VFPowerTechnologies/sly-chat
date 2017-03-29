package io.slychat.messenger.desktop.integration

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject

//FIXME copied from ui-services test
class MockAuthTokenManager(
    var userCredentials: UserCredentials = UserCredentials(SlyAddress(UserId(0), 0), AuthToken("dummy"))
) : AuthTokenManager {
    val newTokenSubject: PublishSubject<AuthToken> = PublishSubject.create()

    private inline fun <T> tryPromise(body: (UserCredentials) -> T): Promise<T, Exception> = try {
        Promise.ofSuccess(body(userCredentials))
    }
    catch (e: Exception) {
        Promise.ofFail(e)
    }

    override val newToken: Observable<AuthToken?>
        get() = newTokenSubject

    //TODO
    override fun setToken(authToken: AuthToken) {
    }

    //TODO
    override fun invalidateToken() {
    }

    override fun <T> bind(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception> = what(userCredentials)

    override fun <T> bindUi(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception> = what(userCredentials)

    override fun <T> map(what: (UserCredentials) -> T): Promise<T, Exception> = tryPromise(what)

    override fun <T> mapUi(what: (UserCredentials) -> T): Promise<T, Exception> = tryPromise(what)
}