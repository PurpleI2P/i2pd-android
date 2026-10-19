package org.purplei2p.i2pd.config

enum class OptionLevel { BASIC, ADVANCED, HIDDEN }

/** Groups of the basic settings screen, in display order. */
enum class OptionGroup { NETWORK, TRANSPORTS, HTTP_PROXY, SOCKS_PROXY, WEB_CONSOLE, SAM, I2CP, LOGGING, LIMITS }

sealed interface OptionWidget {
    data object Switch : OptionWidget

    data object Text : OptionWidget

    /** TCP/UDP port of a local service: 1..65535. */
    data object Port : OptionWidget

    /** Port of the router in the I2P network: 0 (auto) or [RouterPorts.RANGE] except reserved ports. */
    data object RouterPort : OptionWidget

    data class Number(val min: Long, val max: Long) : OptionWidget

    /** One of [values]; with [allowCustom] any other valid value may be typed in. */
    data class Choice(val values: List<String>, val allowCustom: Boolean = false) : OptionWidget
}

/** How an option is presented in the settings UI. Labels come from app string resources by option name. */
data class OptionUi(
    val name: String,
    val level: OptionLevel,
    val widget: OptionWidget,
    val group: OptionGroup? = null,
)

/**
 * Hand-written presentation of i2pd options. Options missing here are still editable:
 * they appear in "Advanced" with a widget derived from their type (see [uiFor]).
 */
object OptionOverlay {
    private val basic: List<OptionUi> = listOf(
        OptionUi("port", OptionLevel.BASIC, OptionWidget.RouterPort, OptionGroup.NETWORK),
        OptionUi("ipv4", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.NETWORK),
        OptionUi("ipv6", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.NETWORK),
        OptionUi("bandwidth", OptionLevel.BASIC, OptionWidget.Choice(listOf("L", "O", "P", "X"), allowCustom = true), OptionGroup.NETWORK),
        OptionUi("share", OptionLevel.BASIC, OptionWidget.Number(0, 100), OptionGroup.NETWORK),
        OptionUi("notransit", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.NETWORK),
        OptionUi("floodfill", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.NETWORK),

        OptionUi("ntcp2.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.TRANSPORTS),
        OptionUi("ssu2.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.TRANSPORTS),
        OptionUi("upnp.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.TRANSPORTS),

        OptionUi("httpproxy.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.HTTP_PROXY),
        OptionUi("httpproxy.address", OptionLevel.BASIC, OptionWidget.Text, OptionGroup.HTTP_PROXY),
        OptionUi("httpproxy.port", OptionLevel.BASIC, OptionWidget.Port, OptionGroup.HTTP_PROXY),
        OptionUi("httpproxy.outproxy", OptionLevel.BASIC, OptionWidget.Text, OptionGroup.HTTP_PROXY),

        OptionUi("socksproxy.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.SOCKS_PROXY),
        OptionUi("socksproxy.address", OptionLevel.BASIC, OptionWidget.Text, OptionGroup.SOCKS_PROXY),
        OptionUi("socksproxy.port", OptionLevel.BASIC, OptionWidget.Port, OptionGroup.SOCKS_PROXY),

        OptionUi("http.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.WEB_CONSOLE),
        OptionUi("http.address", OptionLevel.BASIC, OptionWidget.Text, OptionGroup.WEB_CONSOLE),
        OptionUi("http.port", OptionLevel.BASIC, OptionWidget.Port, OptionGroup.WEB_CONSOLE),

        OptionUi("sam.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.SAM),
        OptionUi("sam.port", OptionLevel.BASIC, OptionWidget.Port, OptionGroup.SAM),

        OptionUi("i2cp.enabled", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.I2CP),
        OptionUi("i2cp.port", OptionLevel.BASIC, OptionWidget.Port, OptionGroup.I2CP),

        OptionUi("loglevel", OptionLevel.BASIC, OptionWidget.Choice(listOf("none", "critical", "error", "warn", "info", "debug")), OptionGroup.LOGGING),

        OptionUi("limits.transittunnels", OptionLevel.BASIC, OptionWidget.Number(2, 65535), OptionGroup.LIMITS),
        OptionUi("persist.profiles", OptionLevel.BASIC, OptionWidget.Switch, OptionGroup.LIMITS),
    )

    /**
     * Options that make no sense in the app: paths and logging set by the app itself
     * (the log goes to the app, see docs/DESIGN.md §5.5), daemon mode, other platforms.
     */
    private val hidden = setOf(
        "help", "version", "conf", "tunconf", "tunnelsdir", "certsdir", "pidfile", "datadir",
        "log", "logfile", "logclftime", "daemon", "service", "svcctl", "insomnia", "close",
        "ntcp", "ssu",
    )
    private val hiddenSections = setOf("unix", "openbsd", "cpuext")

    private val byName = basic.associateBy { it.name }

    /** Basic options present in [schema], in display order. */
    fun basicOptions(schema: OptionSchema): List<OptionUi> = basic.filter { schema[it.name] != null }

    fun uiFor(spec: OptionSpec): OptionUi = byName[spec.name] ?: OptionUi(
        name = spec.name,
        level = if (spec.name in hidden || spec.section in hiddenSections) OptionLevel.HIDDEN else OptionLevel.ADVANCED,
        widget = defaultWidget(spec),
    )

    private fun defaultWidget(spec: OptionSpec): OptionWidget = when (spec.type) {
        OptionType.BOOL -> OptionWidget.Switch
        OptionType.UINT16 -> when {
            spec.name in RouterPorts.OPTIONS -> OptionWidget.RouterPort
            spec.key == "port" || spec.key.endsWith("port") -> OptionWidget.Port
            else -> OptionWidget.Number(0, 65535)
        }
        OptionType.UINT32 -> OptionWidget.Number(0, 4294967295L)
        OptionType.UINT64 -> OptionWidget.Number(0, Long.MAX_VALUE)
        OptionType.INT -> OptionWidget.Number(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        OptionType.STRING, OptionType.DOUBLE -> OptionWidget.Text
    }
}

/** Ports i2pd itself picks for the router when none is set (RouterContext::SelectRandomPort). */
object RouterPorts {
    /** Options holding router ports; 0 means "pick automatically". */
    val OPTIONS = setOf("port", "ntcp2.port", "ssu2.port")

    /** I2P network ports range: rand () % (30777 - 9111) + 9111. */
    val RANGE = 9111..30776

    /** i2p::util::net::IsPortInReservedRange (libi2pd/util.cpp) */
    val RESERVED = setOf(
        9119, 9150, 9306, 9312, 9389, 9418, 9535, 9536, 9695,
        9800, 9899, 10000, 10050, 10051, 10110, 10212,
        10933, 11001, 11112, 11235, 11371, 12222, 12223,
        13075, 13400, 13720, 13721, 13724, 13782, 13783,
        13785, 13786, 15345, 16992, 16993, 16994, 16995,
        17224, 17225, 17500, 18104, 19788, 19812, 19813,
        19814, 19999, 20000, 24465, 24554, 26000, 27000,
        27001, 27002, 27003, 27004, 27005, 27006, 27007,
        27008, 27009, 28000,
    )
}
