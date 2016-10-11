package io.slychat.messenger.testutils

import nl.komponents.kovenant.Context
import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.testMode
import org.junit.rules.ExternalResource

/** JUnit rule for enabling/disabling Kovenant's test mode. */
class KovenantTestModeRule : ExternalResource() {
    private var savedContext: Context? = null

    override fun before() {
        savedContext = Kovenant.context
        Kovenant.testMode()
    }

    override fun after() {
        val context = savedContext
        if (context != null) {
            Kovenant.context = context
            savedContext = null
        }
    }
}