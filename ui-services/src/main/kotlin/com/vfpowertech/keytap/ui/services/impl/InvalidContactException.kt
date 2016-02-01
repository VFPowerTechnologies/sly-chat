package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.UIContactInfo

/** The given contact info didn't correspond to any existing contacts. */
class InvalidContactException(contact: UIContactInfo) : RuntimeException("No such contact: ${contact.name} (id=${contact.id})")