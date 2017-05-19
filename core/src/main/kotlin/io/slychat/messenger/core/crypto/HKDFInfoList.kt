package io.slychat.messenger.core.crypto

/**
 * Global list of HKDF info parameters.
 *
 * @see [KeyVault.deriveKeyFor], [io.slychat.messenger.core.persistence.AccountLocalInfo.getDerivedKeySpec]
 */
object HKDFInfoList {
    /** [KeyVault] serialization. */
    fun keyVault(): HKDFInfo = HKDFInfo("keyvault")

    /** Encrypting [io.slychat.messenger.core.persistence.AccountLocalInfo]. */
    fun accountLocalInfo(): HKDFInfo = HKDFInfo("account-local-info")

    /** Remote AddressBookEntrys. */
    fun remoteAddressBookEntries(): HKDFInfo = HKDFInfo("address-book-entries")

    /** Remote UserMetadata. */
    fun userMetadata(): HKDFInfo = HKDFInfo("user-metadata")

    /** Used to derive subkeys from the local master key. */
    fun localData(): HKDFInfo = HKDFInfo("local-data")

    /** SQLCipher key. Derived from local master key. */
    fun sqlcipher(): HKDFInfo = HKDFInfo("sqlcipher")

    /** Attachment cache thumbnails. Derived from file key. */
    fun thumbnail(fileId: String, resolution: Int): HKDFInfo = HKDFInfo("$fileId-$resolution")
}
