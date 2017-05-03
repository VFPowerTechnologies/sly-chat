@file:JvmName("IOSUtils")
package io.slychat.messenger.ios

import apple.corefoundation.opaque.CFStringRef
import apple.foundation.*
import apple.foundation.c.Foundation.*
import apple.foundation.enums.NSFileCoordinatorReadingOptions
import apple.mobilecoreservices.c.MobileCoreServices
import apple.photos.PHAsset
import apple.photos.PHAssetResource
import apple.photos.PHImageManager
import apple.photos.PHImageRequestOptions
import io.slychat.messenger.services.files.FileInfo
import nl.komponents.kovenant.Kovenant.deferred
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

fun NSURL.bookmark(): NSData {
    val errPtr = newNSErrorPtr()
    val data = bookmarkDataWithOptionsIncludingResourceValuesForKeysRelativeToURLError(
        0,
        null,
        null,
        errPtr
    )

    checkNSError(errPtr, "NSURL.bookmarkData")

    return data
}

fun NSURL.getFileInfo(): FileInfo {
    val errPtr = newNSErrorPtr()
    val keys = nsarray(
        NSURLFileSizeKey(),
        NSURLTypeIdentifierKey(),
        NSURLNameKey()
    )

    val resources = promisedItemResourceValuesForKeysError(keys, errPtr)

    checkNSError(errPtr, "NSURL.resourceValuesForKeys")

    val size = resources[NSURLFileSizeKey()] as NSNumber
    val uti = resources[NSURLTypeIdentifierKey()] as String
    val name = resources[NSURLNameKey()] as String

    return FileInfo(
        name,
        size.longValue(),
        utiToMimeType(uti)
    )
}

private fun PHAsset.getAssetOriginalFileName(): String? {
    val resources = PHAssetResource.assetResourcesForAsset(this)
    if (resources.isEmpty())
        return null

    val resource = resources.first()

    return resource.originalFilename()
}

fun <R> withAssetData(asset: PHAsset, body: (data: NSData?, dataUTI: String?) -> R): R {
    val manager = PHImageManager.defaultManager()

    val options = PHImageRequestOptions.alloc().init()
    options.isNetworkAccessAllowed = false
    //using sync here causes a segfault on the worker thread for whatever reason; seems to be some bug with MOE or
    //something since this works fine via swift
    //so we just hack around it for now since this works even though it's a waste of a worker thread
    //TODO check if this affects 1.3 as well
    //options.isSynchronous = true
    val d = deferred<Pair<NSData?, String?>, Exception>()

    manager.requestImageDataForAssetOptionsResultHandler(asset, options) { data, dataUTI, orientation, info ->
        d.resolve(data to dataUTI)
    }

    val pair = d.promise.get()

    return body(pair.first, pair.second)
}

fun PHAsset.getFileInfo(): FileInfo {
    val originalFileName = this.getAssetOriginalFileName() ?: throw FileNotFoundException("No filename set")
    val fileInfo = withAssetData(this) { data, dataUTI ->
        if (data == null || dataUTI == null)
            null
        else {
            val mimeType = utiToMimeType(dataUTI)
            FileInfo(originalFileName, data.length(), mimeType)
        }
    } ?: throw FileNotFoundException("No data found for asset")

    return fileInfo
}

fun NSURL.fetchAsset(): PHAsset? {
    val result = PHAsset.fetchAssetsWithALAssetURLsOptions(nsarray(this), null)
    if (result.count() <= 0)
        return null

    return result.firstObject()
}
