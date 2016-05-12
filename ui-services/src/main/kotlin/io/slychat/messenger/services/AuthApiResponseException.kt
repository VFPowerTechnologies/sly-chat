package io.slychat.messenger.services

class AuthApiResponseException(val errorMessage: String): RuntimeException(errorMessage)