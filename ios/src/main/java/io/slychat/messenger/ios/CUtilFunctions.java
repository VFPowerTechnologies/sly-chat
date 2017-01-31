package io.slychat.messenger.ios;

import org.moe.natj.c.CRuntime;
import org.moe.natj.c.ann.CFunction;
import org.moe.natj.general.ann.Runtime;

@Runtime(CRuntime.class)
public class CUtilFunctions {
    /** Returns 0 on success, otherwise errno is returned. */
    @CFunction
    public static native int ignoreSIGPIPE();

    /** Returns 0 on success, otherwise errno is returned. */
    @CFunction
    public static native int hookSignalCrashHandler(String dumpFilePath);
}
