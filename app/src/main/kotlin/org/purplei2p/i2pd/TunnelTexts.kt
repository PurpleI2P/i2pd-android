package org.purplei2p.i2pd

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.purplei2p.i2pd.config.DestinationUser
import org.purplei2p.i2pd.config.TunnelInfo
import org.purplei2p.i2pd.config.TunnelIssue
import org.purplei2p.i2pd.config.TunnelType

fun tunnelTypeLabel(type: TunnelType): Int = when (type) {
    TunnelType.CLIENT -> R.string.tunnel_type_client
    TunnelType.UDP_CLIENT -> R.string.tunnel_type_udpclient
    TunnelType.SOCKS -> R.string.tunnel_type_socks
    TunnelType.HTTP_PROXY -> R.string.tunnel_type_httpproxy
    TunnelType.SERVER -> R.string.tunnel_type_server
    TunnelType.HTTP -> R.string.tunnel_type_http
    TunnelType.IRC -> R.string.tunnel_type_irc
    TunnelType.UDP_SERVER -> R.string.tunnel_type_udpserver
    TunnelType.WEBSOCKS -> R.string.tunnel_type_websocks
    TunnelType.TORRENTS -> R.string.tunnel_type_torrents
}

@Composable
fun tunnelTypeText(info: TunnelInfo): String = info.type?.let { stringResource(tunnelTypeLabel(it)) } ?: (info.typeName ?: "?")

private val PARAM_LABELS = mapOf(
    "destination" to R.string.tparam_destination,
    "destinationport" to R.string.tparam_destinationport,
    "address" to R.string.tparam_address,
    "keys" to R.string.tparam_keys,
    "outproxy" to R.string.tparam_outproxy,
    "addresshelper" to R.string.tparam_addresshelper,
    "senduseragent" to R.string.tparam_senduseragent,
    "host" to R.string.tparam_host,
    "inport" to R.string.tparam_inport,
    "accesslist" to R.string.tparam_accesslist,
    "hostoverride" to R.string.tparam_hostoverride,
    "i2pheaders" to R.string.tparam_i2pheaders,
    "webircpassword" to R.string.tparam_webircpassword,
    "ssl" to R.string.tparam_ssl,
    "torrentsdir" to R.string.tparam_torrentsdir,
    "trackers" to R.string.tparam_trackers,
    "inbound.length" to R.string.tparam_inbound_length,
    "inbound.quantity" to R.string.tparam_inbound_quantity,
    "outbound.length" to R.string.tparam_outbound_length,
    "outbound.quantity" to R.string.tparam_outbound_quantity,
    "inbound.lengthVariance" to R.string.tparam_inbound_variance,
    "outbound.lengthVariance" to R.string.tparam_outbound_variance,
    "i2cp.leaseSetType" to R.string.tparam_leaseset_type,
    "i2cp.leaseSetEncType" to R.string.tparam_leaseset_enc,
    "i2cp.dontPublishLeaseSet" to R.string.tparam_dont_publish,
)

@Composable
fun tunnelParamLabel(key: String, type: TunnelType): String {
    if (key == "port") {
        return stringResource(if (type.kind == TunnelType.Kind.SERVER) R.string.tparam_port_server else R.string.tparam_port_client)
    }
    return PARAM_LABELS[key]?.let { stringResource(it) } ?: key
}

/** One line describing where a tunnel listens and where it leads. */
fun tunnelSummary(info: TunnelInfo): String {
    val v = info.values
    return when (info.type?.kind) {
        TunnelType.Kind.CLIENT -> {
            val local = "${v["address"] ?: "127.0.0.1"}:${v["port"] ?: "?"}"
            val destination = v["destination"]?.let { dest -> v["destinationport"]?.takeIf { it != "0" }?.let { "$dest:$it" } ?: dest }
            if (destination != null) "$local → $destination" else local
        }
        TunnelType.Kind.SERVER -> "${v["keys"] ?: "?"} → ${v["host"] ?: "?"}:${v["port"] ?: "?"}"
        TunnelType.Kind.OTHER -> v["torrentsdir"].orEmpty()
        null -> ""
    }
}

@Composable
fun destinationUserText(user: DestinationUser): String = when {
    user.file != null -> user.name
    user.name == "httpproxy" -> stringResource(R.string.proxy_name_httpproxy)
    user.name == "socksproxy" -> stringResource(R.string.proxy_name_socksproxy)
    else -> user.name
}

@Composable
fun tunnelIssueText(issue: TunnelIssue): String = when (issue) {
    is TunnelIssue.InvalidLine -> stringResource(R.string.tissue_invalid_line, issue.file, issue.line + 1)
    is TunnelIssue.EntryOutsideSection -> stringResource(R.string.tissue_outside_section, issue.file, issue.key)
    is TunnelIssue.DuplicateSection -> stringResource(R.string.tissue_duplicate_section, issue.file, issue.section)
    is TunnelIssue.DuplicateKey -> stringResource(R.string.tissue_duplicate_key, issue.file, issue.section, issue.key)
    is TunnelIssue.MissingType -> stringResource(R.string.tissue_missing_type, issue.section)
    is TunnelIssue.UnknownType -> stringResource(R.string.tissue_unknown_type, issue.section, issue.type)
    is TunnelIssue.MissingParam -> stringResource(R.string.tissue_missing_param, issue.section, issue.key)
    is TunnelIssue.InvalidValue -> stringResource(R.string.tissue_invalid_value, issue.section, issue.key, issue.value)
    is TunnelIssue.PortConflict -> stringResource(R.string.tissue_port_conflict, issue.section, issue.port, issue.others.joinToString(", "))
}

/** The tunnel an issue is about, null for issues of the whole file. */
val TunnelIssue.tunnel: String?
    get() = when (this) {
        is TunnelIssue.InvalidLine, is TunnelIssue.EntryOutsideSection -> null
        is TunnelIssue.DuplicateSection -> section
        is TunnelIssue.DuplicateKey -> section
        is TunnelIssue.MissingType -> section
        is TunnelIssue.UnknownType -> section
        is TunnelIssue.MissingParam -> section
        is TunnelIssue.InvalidValue -> section
        is TunnelIssue.PortConflict -> section
    }
