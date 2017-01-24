package io.slychat.messenger.ios;

import org.moe.natj.c.CRuntime;
import org.moe.natj.c.ann.CFunction;
import org.moe.natj.general.ann.Runtime;

@Runtime(CRuntime.class)
public class CUtilFunctions {
    @CFunction
    public static native int ignoreSIGPIPE();
}
