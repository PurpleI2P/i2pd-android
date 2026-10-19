package org.purplei2p.i2pd.nativelib

/** Receives i2pd fatal errors. Called synchronously from a native thread, possibly right before exit(). */
fun interface FatalHandler {
    fun onFatal(message: String)
}

/**
 * Raw JNI bindings to libi2pd.so.
 *
 * Methods are registered in JNI_OnLoad (native/jni/jni_bridge.cpp), so the class name
 * and method signatures must stay in sync with kMethods there.
 *
 * i2pd keeps global state that cannot be re-initialized, so [init] may be called
 * only once per process: a restart means a new process.
 */
object I2pdNative {
    init {
        System.loadLibrary("i2pd")
    }

    /** i2pd version the library was built from, e.g. "3.0.0". */
    @JvmStatic
    external fun version(): String

    /** ABI the library was compiled for. */
    @JvmStatic
    external fun abi(): String

    @JvmStatic
    external fun setFatalHandler(handler: FatalHandler?)

    /** Parses command line and config. Returns null on success, otherwise an error message. */
    @JvmStatic
    external fun init(dataDir: String, args: Array<String>, language: String): String?

    /** Starts the router. Returns null on success, otherwise an error message. */
    @JvmStatic
    external fun start(): String?

    /** Stops the router, blocking. */
    @JvmStatic
    external fun stop()

    /** Fills [out] (at least [Status.SIZE] long) with the current status. Returns false if the router is not running. */
    @JvmStatic
    external fun status(out: LongArray): Boolean

    @JvmStatic
    external fun setAcceptsTransit(accept: Boolean)

    @JvmStatic
    external fun reloadTunnels()

    @JvmStatic
    external fun setOnline(online: Boolean)

    /** Value of an option after [init] (e.g. "http.port"), null if unknown or not initialized. */
    @JvmStatic
    external fun option(name: String): String?

    /**
     * Log lines with seq > [sinceSeq], at most [max]. Seq of the first returned line
     * (or the next seq if nothing returned) is written to outFirstSeq[0].
     */
    @JvmStatic
    external fun readLog(sinceSeq: Long, max: Int, outFirstSeq: LongArray): Array<String>

    /**
     * All i2pd options, one per line: name, type, has default (0/1), default, description;
     * tab separated, with backslash, tab and newline escaped. Null on error.
     *
     * Initializes i2pd option descriptions, so after it [init] fails in this process:
     * call it only where the router does not run (the main process).
     */
    @JvmStatic
    external fun describeOptions(): String?

    /** Indexes of [status] array, keep in sync with StatusIndex in jni_bridge.cpp. */
    object Status {
        const val STATUS_V4 = 0
        const val STATUS_V6 = 1
        const val ERROR_V4 = 2
        const val ERROR_V6 = 3
        const val TESTING_V4 = 4
        const val TESTING_V6 = 5
        const val UPTIME = 6
        const val ACCEPTS_TRANSIT = 7
        const val FLOODFILL = 8
        const val BANDWIDTH_IN = 9
        const val BANDWIDTH_OUT = 10
        const val BANDWIDTH_TRANSIT = 11
        const val TOTAL_RECEIVED = 12
        const val TOTAL_SENT = 13
        const val TOTAL_TRANSIT = 14
        const val TUNNELS_IN = 15
        const val TUNNELS_OUT = 16
        const val TUNNELS_TRANSIT = 17
        const val TUNNEL_SUCCESS_RATE = 18
        const val ROUTERS = 19
        const val FLOODFILLS = 20
        const val LEASE_SETS = 21
        const val SERVICES = 22
        const val NET_ID = 23
        const val SIZE = 24

        // Bits of SERVICES
        const val SERVICE_HTTP_PROXY = 1L shl 0
        const val SERVICE_SOCKS_PROXY = 1L shl 1
        const val SERVICE_SAM = 1L shl 2
        const val SERVICE_BOB = 1L shl 3
        const val SERVICE_I2CP = 1L shl 4
        const val SERVICE_WEB_CONSOLE = 1L shl 5
    }
}
