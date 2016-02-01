package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.UIContactDetails

/** The given contact info didn't correspond to any existing contacts. */
class InvalidContactException(contact: UIContactDetails) : RuntimeException("No such contact: ${contact.name} (id=${contact.id})")