package io.slychat.messenger.services.auth

import io.slychat.messenger.core.AuthToken

data class AuthTokenRefreshResult(val authToken: AuthToken)