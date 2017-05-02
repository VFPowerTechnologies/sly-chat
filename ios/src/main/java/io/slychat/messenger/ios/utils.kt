@file:JvmName("IOSUtils")
package io.slychat.messenger.ios

import apple.corefoundation.opaque.CFStringRef
import apple.foundation.*
import apple.foundation.enums.NSFileCoordinatorReadingOptions
import apple.mobilecoreservices.c.MobileCoreServices
import org.moe.natj.general.ptr.Ptr
import org.moe.natj.general.ptr.impl.PtrFactory
import org.moe.natj.objc.ObjCRuntime
import java.io.FileNotFoundException

/**
 * Utility for creating an NSArray from a variable set of objects.
 *
 * Handles appending the null terminate during construction, as well as casting to the expected type, as
 * arrayWithObjects ignores the given type and just returns an NSArray<*>.
 */
fun <T> nsarray(first: T, vararg items: T): NSArray<T> {
    @Suppress("UNCHECKED_CAST")
    return NSArray.arrayWithObjects(first, *items, null) as NSArray<T>
}

/** Attempts to convert the given UTI to a MIME type. If no MIME type exists for the given UTI, application/octet-stream is used. */
fun utiToMimeType(uti: String): String {
    val cs = ObjCRuntime.cast(NSString.stringWithString(uti), CFStringRef::class.java)
    val cfMimeTypes = MobileCoreServices.UTTypeCopyAllTagsWithClass(cs, MobileCoreServices.kUTTagClassMIMEType())

    if (cfMimeTypes == null)
        return "application/octet-stream"

    @Suppress("UNCHECKED_CAST")
    val mimeTypes: NSArray<String> = ObjCRuntime.cast(cfMimeTypes, NSArray::class.java) as NSArray<String>

    //use preferred tag
    return mimeTypes[0]
}

/** Returns a new NSError*. */
fun newNSErrorPtr(): Ptr<NSError> {
    return PtrFactory.newObjectReference(NSError::class.java)
}

/**
 * Checks if pointer is non-null, and raises an exception if so.
 *
 * Raises RuntimeException unless a more specific exception is known for the domain/code pair.
 */
fun checkNSError(errPtr: Ptr<NSError>, message: String) {
    val err = errPtr.get()
    if (err != null) {
        val fullMessage = "$message: ${err.description()}"

        if (err.domain() == "NSCocoaErrorDomain" && err.code() == 260L)
            throw FileNotFoundException(fullMessage)

        throw RuntimeException(fullMessage)
    }
}

/** Toll-free bridge cast from CFString* to NSString*. */
fun CFStringRef.toNSString(): NSString {
    return ObjCRuntime.cast(this, NSString::class.java)
}

/**
 * Attempts to access a security-scoped URL.
 *
 * @throws SecurityException If access fails.
 */
fun <R> NSURL.access(body: () -> R): R {
    if (!startAccessingSecurityScopedResource())
        throw SecurityException("Failed to access security url")

    return try {
        body()
    }
    finally {
        stopAccessingSecurityScopedResource()
    }
}

/**
 * Runs the given function inside an NSFileCoordinator block.
 *
 * @note If a security-scoped URL, must already be opened for access.
 */
fun <R> NSURL.openForReadWithCoordinator(body: (NSFileHandle) -> R) {
    val coordinator = NSFileCoordinator.alloc().init()

    val errPtr = newNSErrorPtr()

    access {
        coordinator.coordinateReadingItemAtURLOptionsErrorByAccessor(this, NSFileCoordinatorReadingOptions.ForUploading, errPtr) {
            val errPtr = newNSErrorPtr()
            val handle = NSFileHandle.fileHandleForReadingFromURLError(it, errPtr)
            checkNSError(errPtr, "NSFileHandle.fileHandleForReadingFromURL")

            body(handle)
        }

        checkNSError(errPtr, "coordinateReadingItemAtURL")
    }
}
