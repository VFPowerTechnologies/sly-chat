package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

/**
 * Various utilities for development use.
 */
@JSToJavaGenerate("DevelService")
interface UIDevelService {
    /**
     * Mimics receiving a message from a contact.
     */
    fun receiveFakeMessage(contact: UIContactDetails, message: String)
}