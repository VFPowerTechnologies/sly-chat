package io.slychat.messenger.core.integration.web

import org.junit.rules.ExternalResource

class IsDevServerRunningClassRule : ExternalResource() {
    override fun before() {
        isDevServerRunning()
    }
}