package io.slychat.messenger.core.crypto

object HKDFInfoList {
    fun keyVault(): HKDFInfo = HKDFInfo("keyvault")

    fun accountLocalInfo(): HKDFInfo = HKDFInfo("account-local-info")

    fun remoteAddressBookEntries(): HKDFInfo = HKDFInfo("address-book-entries")

    fun userMetadata(): HKDFInfo = HKDFInfo("user-metadata")

    /** Used to derive subkeys from the local master key. */
    fun localData(): HKDFInfo = HKDFInfo("local-data")

    fun sqlcipher(): HKDFInfo = HKDFInfo("sqlcipher")
}
