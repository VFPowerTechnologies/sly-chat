package io.slychat.messenger.services.files

class InvalidTransferException(val transferId: String) : RuntimeException("Transfer $transferId doesn't exist")