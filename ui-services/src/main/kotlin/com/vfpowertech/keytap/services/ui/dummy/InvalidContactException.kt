package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.ui.UIContactDetails

/** The given contact info didn't correspond to any existing contacts. */
class InvalidContactException(contact: UIContactDetails) : RuntimeException("No such contact: ${contact.name} (email=${contact.email})")