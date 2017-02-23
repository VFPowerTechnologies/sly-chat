package io.slychat.messenger.core.files

//this is a mix of saved db statuses (IN_PROGRESS|ERROR|CANCELLED) and just TransferManager statuses (QUEUED for
//IN_PROGRESS transfers that aren't currently transfering)
enum class TransferStatus {
    IN_PROGRESS,
    QUEUED,
    ERROR,
    CANCELLED
}