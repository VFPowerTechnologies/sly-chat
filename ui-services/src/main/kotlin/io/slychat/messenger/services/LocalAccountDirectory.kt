package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AccountInfo

/**
 * Responsible for looking up existing AccountInfo any users that have previously logged in on this machine.
 */
interface LocalAccountDirectory {
    fun findAccountFor(emailOrPhoneNumber: String): AccountInfo?

    fun findAccountFor(userId: UserId): AccountInfo?
}