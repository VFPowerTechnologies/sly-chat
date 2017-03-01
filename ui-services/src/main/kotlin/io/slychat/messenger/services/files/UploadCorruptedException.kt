package io.slychat.messenger.services.files

/** Occurs whenever an uploaded part's returned checksum doesn't match the expected value. */
class UploadCorruptedException : Exception()