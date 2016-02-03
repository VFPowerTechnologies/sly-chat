package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

/**
 * Various utilities for development use.
 */
@JSToJavaGenerate
interface DevelService {
    /**
     * Mimics receiving a message from a contact.
     */
    fun receiveFakeMessage(contact: UIContactDetails, message: String)
}