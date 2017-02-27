package io.slychat.messenger.core.integration.web

import org.junit.rules.ExternalResource

class IsDevFileServerRunningClassRule : ExternalResource() {
    override fun before() {
        isDevFileServerRunning()
    }
}