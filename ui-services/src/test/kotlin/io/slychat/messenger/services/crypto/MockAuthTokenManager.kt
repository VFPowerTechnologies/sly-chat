package io.slychat.messenger.services.crypto

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import rx.Observable

/** Just runs the given functions with dummy credentials. */
class MockAuthTokenManager : AuthTokenManager {
    private val dummyCreds = UserCredentials(SlyAddress(UserId(0), 0), AuthToken("dummy"))

    private inline fun <T> tryPromise(body: (UserCredentials) -> T): Promise<T, Exception> = try {
        Promise.ofSuccess(body(dummyCreds))
    }
    catch (e: Exception) {
        Promise.ofFail(e)
    }

    override val newToken: Observable<AuthToken?>
        get() = throw UnsupportedOperationException()

    override fun setToken(authToken: AuthToken) {
        throw UnsupportedOperationException()
    }

    override fun invalidateToken() {
        throw UnsupportedOperationException()
    }

    override fun <T> bind(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception> = what(dummyCreds)

    override fun <T> bindUi(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception> = what(dummyCreds)

    override fun <T> map(what: (UserCredentials) -> T): Promise<T, Exception> = tryPromise(what)

    override fun <T> mapUi(what: (UserCredentials) -> T): Promise<T, Exception> = tryPromise(what)
}