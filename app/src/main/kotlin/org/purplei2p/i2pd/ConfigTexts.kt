package org.purplei2p.i2pd

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.purplei2p.i2pd.config.ConfigIssue
import org.purplei2p.i2pd.config.OptionGroup
import org.purplei2p.i2pd.config.RouterPorts
import org.purplei2p.i2pd.config.ValueError

/** Localized labels of the basic options; other options are shown by their i2pd names. */
private val LABELS = mapOf(
    "port" to R.string.opt_port,
    "ipv4" to R.string.opt_ipv4,
    "ipv6" to R.string.opt_ipv6,
    "bandwidth" to R.string.opt_bandwidth,
    "share" to R.string.opt_share,
    "notransit" to R.string.opt_notransit,
    "floodfill" to R.string.opt_floodfill,
    "ntcp2.enabled" to R.string.opt_ntcp2_enabled,
    "ssu2.enabled" to R.string.opt_ssu2_enabled,
    "upnp.enabled" to R.string.opt_upnp_enabled,
    "httpproxy.outproxy" to R.string.opt_outproxy,
    "loglevel" to R.string.opt_loglevel,
    "limits.transittunnels" to R.string.opt_limits_transittunnels,
    "persist.profiles" to R.string.opt_persist_profiles,
)

/** Service options share labels: they are shown under their group title. */
private val SERVICE_SECTIONS = setOf("httpproxy", "socksproxy", "http", "sam", "i2cp")

@Composable
fun optionLabel(name: String): String {
    LABELS[name]?.let { return stringResource(it) }
    val section = name.substringBefore('.', "")
    if (section in SERVICE_SECTIONS) {
        when (name.substringAfter('.')) {
            "enabled" -> return stringResource(R.string.opt_enabled)
            "address" -> return stringResource(R.string.opt_address)
            "port" -> return stringResource(R.string.opt_service_port)
        }
    }
    return name
}

/** Human readable value; [isDefault] marks values not written in the file. */
@Composable
fun optionValueText(name: String, value: String?, isDefault: Boolean): String {
    val text = when {
        value.isNullOrEmpty() -> stringResource(R.string.value_empty)
        name in RouterPorts.OPTIONS && value == "0" -> stringResource(R.string.router_port_auto)
        name == "bandwidth" -> bandwidthText(value) ?: value
        else -> value
    }
    return if (isDefault) stringResource(R.string.value_default, text) else text
}

@Composable
fun bandwidthText(value: String): String? = when (value) {
    "L" -> stringResource(R.string.bandwidth_l)
    "O" -> stringResource(R.string.bandwidth_o)
    "P" -> stringResource(R.string.bandwidth_p)
    "X" -> stringResource(R.string.bandwidth_x)
    else -> null
}

fun groupTitle(group: OptionGroup): Int = when (group) {
    OptionGroup.NETWORK -> R.string.group_network
    OptionGroup.TRANSPORTS -> R.string.group_transports
    OptionGroup.HTTP_PROXY -> R.string.group_http_proxy
    OptionGroup.SOCKS_PROXY -> R.string.group_socks_proxy
    OptionGroup.WEB_CONSOLE -> R.string.group_web_console
    OptionGroup.SAM -> R.string.group_sam
    OptionGroup.I2CP -> R.string.group_i2cp
    OptionGroup.LOGGING -> R.string.group_logging
    OptionGroup.LIMITS -> R.string.group_limits
}

/** Message for a value typed into an option editor. */
@Composable
fun valueErrorText(name: String, error: ValueError): String = stringResource(
    when (error) {
        ValueError.NOT_BOOLEAN -> R.string.error_not_boolean
        ValueError.NOT_NUMBER -> R.string.error_not_number
        ValueError.OUT_OF_RANGE -> if (name.endsWith("port")) R.string.error_out_of_range_port else R.string.error_out_of_range
        ValueError.NOT_ALLOWED -> R.string.error_not_allowed
        ValueError.OUTSIDE_RECOMMENDED_RANGE -> R.string.warning_router_port_range
        ValueError.RESERVED_PORT -> R.string.warning_reserved_port
    },
)

@Composable
fun issueText(issue: ConfigIssue): String = when (issue) {
    is ConfigIssue.UnknownOption -> stringResource(R.string.issue_unknown, issue.name)
    is ConfigIssue.Duplicate -> stringResource(R.string.issue_duplicate, issue.name)
    is ConfigIssue.PortConflict -> stringResource(R.string.issue_port_conflict, issue.port, issue.options.joinToString(", "))
    is ConfigIssue.InvalidValue -> stringResource(
        when (issue.reason) {
            ValueError.NOT_BOOLEAN -> R.string.issue_not_boolean
            ValueError.NOT_NUMBER -> R.string.issue_not_number
            ValueError.OUT_OF_RANGE -> R.string.issue_out_of_range
            ValueError.NOT_ALLOWED -> R.string.issue_not_allowed
            ValueError.OUTSIDE_RECOMMENDED_RANGE -> R.string.issue_outside_range
            ValueError.RESERVED_PORT -> R.string.issue_reserved_port
        },
        issue.name,
        issue.value,
    )
}
