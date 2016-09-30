package io.slychat.messenger.core.crypto

object HKDFInfoList {
    //TODO should add userId to these
    fun keyVaultMasterKey(): HKDFInfo = HKDFInfo("keyvault-master-key")
    fun keyVaultKeyPair(): HKDFInfo = HKDFInfo("keyvault-keypair")
    fun keyVaultAnonymizingData(): HKDFInfo = HKDFInfo("keyvault-anonymizing-data")
    fun addressBookEntries(): HKDFInfo = HKDFInfo("address-book-entries")
    fun jsonSessionData(): HKDFInfo = HKDFInfo("json-session-data")
    fun jsonAccountParams(): HKDFInfo = HKDFInfo("json-account-params")
}
