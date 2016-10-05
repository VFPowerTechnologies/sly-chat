package io.slychat.messenger.desktop.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface CLibrary extends Library {
    CLibrary INSTANCE  = (CLibrary) Native.loadLibrary(
        "c",
        CLibrary.class
    );

    int umask(int mask);
}