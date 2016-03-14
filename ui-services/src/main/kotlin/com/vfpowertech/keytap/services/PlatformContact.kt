package com.vfpowertech.keytap.services

import java.util.*

/** Helper class for incrementally building up a Contact. */
class PlatformContactBuilder {
    var name: String? = null
    val emails = ArrayList<String>()
    val phoneNumbers = ArrayList<String>()

    fun build(): PlatformContact = PlatformContact(name!!, emails, phoneNumbers)
}

/** Platform contact info. */
data class PlatformContact(val name: String, val emails: List<String>, val phoneNumbers: List<String>)