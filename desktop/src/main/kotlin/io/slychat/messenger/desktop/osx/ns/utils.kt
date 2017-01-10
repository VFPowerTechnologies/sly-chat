package io.slychat.messenger.desktop.osx.ns

fun Boolean.toObjc(): Byte {
    return if (this)
        return 1
    else
        return 0
}
