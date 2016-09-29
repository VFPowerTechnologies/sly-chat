package io.slychat.messenger.core.crypto

object HKDFInfo {
    //TODO maybe add userId?
    fun keyVaultMasterKey(): ByteArray = "keyvault-master-key".toByteArray()
    fun keyVaultKeyPair(): ByteArray = "keyvault-keypair".toByteArray()
    fun keyVaultAnonymizingData(): ByteArray = "keyvault-anonymizing-data".toByteArray()
    fun addressBookEntries(): ByteArray = "address-book-entries".toByteArray()
    fun jsonSessionData(): ByteArray = "json-session-data".toByteArray()
    fun jsonAccountParams(): ByteArray = "json-account-params".toByteArray()
}