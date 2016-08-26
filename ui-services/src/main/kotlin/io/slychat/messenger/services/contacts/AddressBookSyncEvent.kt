package io.slychat.messenger.services.contacts

sealed class AddressBookSyncEvent {
    abstract val info: AddressBookSyncJobInfo

    class Begin(override val info: AddressBookSyncJobInfo) : AddressBookSyncEvent()
    class End(override val info: AddressBookSyncJobInfo, val result: AddressBookSyncResult) : AddressBookSyncEvent()
}