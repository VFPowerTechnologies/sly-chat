package io.slychat.messenger.ios

import apple.c.Globals
import apple.enums.qos_class_t.QOS_CLASS_BACKGROUND
import apple.struct.sockaddr
import apple.struct.sockaddr_in
import apple.systemconfiguration.c.SystemConfiguration
import apple.systemconfiguration.c.SystemConfiguration.*
import apple.systemconfiguration.enums.SCNetworkReachabilityFlags
import apple.systemconfiguration.opaque.SCNetworkReachabilityRef
import org.moe.natj.c.CRuntime
import org.moe.natj.general.Pointer
import org.moe.natj.general.ptr.impl.PtrFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class ReachabilityException(
    message: String,
    val errorCode: Int
) : RuntimeException("$message: ${SystemConfiguration.SCErrorString(errorCode)}") {
    constructor(message: String) : this(message, SystemConfiguration.SCError())
}

private fun sockaddr_in.toSockAddr(): sockaddr {
    val ctor = sockaddr::class.java.getDeclaredConstructor(Pointer::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(peer)
}

enum class ConnectionStatus {
    WIFI,
    WWAN,
    NONE
}

class Reachability {
    companion object {
        //AF_INET (Developer/Platforms/<platform>.platform/Developer/SDKs/<platform>.sdk/usr/include/sys/socket.h)
        private const val AF_INET: Byte = 2
    }

    private var ref: SCNetworkReachabilityRef? = null
    private val connectionStatusSubject = BehaviorSubject.create<ConnectionStatus>(ConnectionStatus.NONE)
    val connectionStatus: Observable<ConnectionStatus>
        get() = connectionStatusSubject

    init {
        val addr = sockaddr_in()
        addr.setSin_len(CRuntime.sizeOfNativeObject(sockaddr_in::class.java).toByte())
        addr.setSin_family(AF_INET)

        @Suppress("UNCHECKED_CAST")
        val ref = SCNetworkReachabilityCreateWithAddress(null, addr.toSockAddr())
        if (ref == null)
            throw ReachabilityException("SCNetworkReachabilityCreateWithAddress() failed")

        this.ref = ref

        register(ref)
    }

    private fun register(ref: SCNetworkReachabilityRef) {
        val queue = Globals.dispatch_get_global_queue(QOS_CLASS_BACKGROUND.toLong(), 0)
        if (SCNetworkReachabilitySetDispatchQueue(ref, queue) != 1.toByte())
            throw ReachabilityException("SCNetworkReachabilitySetDispatchQueue() failed")

        if (SCNetworkReachabilitySetCallback(ref, { target, flags, info ->
            onChange(flags)
        }, null) != 1.toByte()) {
            SCNetworkReachabilitySetDispatchQueue(ref, null)

            throw ReachabilityException("SCNetworkReachabilitySetCallback() failed")
        }

        updateConnectionStatus(flags)
    }

    fun unregister() {
        val ref = this.ref ?: return

        SCNetworkReachabilitySetDispatchQueue(ref, null)
        SCNetworkReachabilitySetCallback(ref, null, null)
    }

    private fun onChange(newFlags: Int) {
        Globals.dispatch_async(Globals.dispatch_get_main_queue()) {
            updateConnectionStatus(newFlags)
        }
    }

    private fun isTransient(flags: Int): Boolean {
        val f = SCNetworkReachabilityFlags.ConnectionRequired or SCNetworkReachabilityFlags.TransientConnection

        return (flags and f) == f
    }

    private fun updateConnectionStatus(flags: Int) {
        val status = if ((flags and SCNetworkReachabilityFlags.Reachable) == 0)
            ConnectionStatus.NONE
        else if (isTransient(flags))
            ConnectionStatus.NONE
        else if ((flags and SCNetworkReachabilityFlags.IsWWAN) != 0)
            ConnectionStatus.WWAN
        else
            ConnectionStatus.WIFI

        connectionStatusSubject.onNext(status)
    }

    private val flags: Int
        get() {
            val i = PtrFactory.newIntPtr(1, true, false)
            if (SCNetworkReachabilityGetFlags(ref, i) != 1.toByte())
                throw ReachabilityException("SCNetworkReachabilityGetFlags() failed")

            return i.value
        }
}

