package io.slychat.messenger.services.auth

class AuthApiResponseException(val errorMessage: String): RuntimeException(errorMessage)