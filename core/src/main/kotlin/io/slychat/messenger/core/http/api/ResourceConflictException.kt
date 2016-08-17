package io.slychat.messenger.core.http.api

/** Used to indicate inability to lock a resource for updates. */
class ResourceConflictException : RuntimeException("Resource conflict")