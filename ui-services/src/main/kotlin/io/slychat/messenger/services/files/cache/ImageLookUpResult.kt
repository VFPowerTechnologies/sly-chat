package io.slychat.messenger.services.files.cache

import java.io.InputStream

class ImageLookUpResult(val inputStream: InputStream?, val isDeleted: Boolean, val isOriginalPresent: Boolean)