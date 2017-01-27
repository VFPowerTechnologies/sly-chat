-dontobfuscate
-dontnote **

#ignore warnings about sun.misc.Unsafe
#as well as other internal platform-specific or optional things
-dontwarn rx.internal.util.**

-dontwarn com.fasterxml.jackson.databind.ext.**

-dontwarn org.joda.convert.**

-dontwarn nl.komponents.kovenant.unsafe.**

-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

-keep class !org.joda.**,!kotlin.**,!com.fasterxml.jackson.**,!org.spongycastle.**,!com.google.**,!android.**,!rx.**,!org.whispersystems.** { *; }

-keep class org.whispersystems.curve25519.OpportunisticCurve25519Provider { *; }

-keepclassmembers class io.slychat.messenger.core.OSInfo { *; }

#android-logger doesn't implement certain optional things
-dontwarn org.slf4j.impl.**
