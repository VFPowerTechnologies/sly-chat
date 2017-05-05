package io.slychat.messenger.services.files.cache

import java.io.InputStream

/**
 * Result of an image look up by the UI.
 *
 * If inputStream is null, but isDeleted is false, then the file will be fetched and cached and an event will be emitted.
 *
 * isOriginal is used internally.
 */
class ImageLookUpResult(val inputStream: InputStream?, val isDeleted: Boolean, val isOriginalPresent: Boolean)