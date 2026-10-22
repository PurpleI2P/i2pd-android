package org.purplei2p.i2pd.config

import java.security.MessageDigest
import java.util.zip.CRC32

/** Tunnel types of ClientContext::ReadTunnels. */
enum class TunnelType(val id: String, val kind: Kind, val creatable: Boolean = true) {
    CLIENT("client", Kind.CLIENT),
    UDP_CLIENT("udpclient", Kind.CLIENT),
    SOCKS("socks", Kind.CLIENT),
    HTTP_PROXY("httpproxy", Kind.CLIENT),
    SERVER("server", Kind.SERVER),
    HTTP("http", Kind.SERVER),
    IRC("irc", Kind.SERVER),
    UDP_SERVER("udpserver", Kind.SERVER),

    /** Deprecated in i2pd: such tunnels are not started. */
    WEBSOCKS("websocks", Kind.CLIENT, creatable = false),

    /** BitTorrent client of i2pd 3.0 with an optional Transmission-compatible RPC server. */
    TORRENTS("torrents", Kind.OTHER);

    enum class Kind { CLIENT, SERVER, OTHER }

    companion object {
        fun of(id: String?): TunnelType? = entries.firstOrNull { it.id == id }
    }
}

enum class TunnelParamKind { TEXT, PORT, BOOL, NUMBER, DESTINATION, KEYS, SIGNATURE_TYPE }

/** Where a parameter is shown in the tunnel form: right away or in its "additional" part. */
enum class TunnelParamLevel { MAIN, ADVANCED }

/**
 * A tunnel parameter with the default i2pd uses when it is not set. [serverDefault] differs for
 * tunnels whose I2CP options are read as a server's (server types and torrents).
 */
data class TunnelParam(
    val key: String,
    val kind: TunnelParamKind,
    val required: Boolean = false,
    val default: String? = null,
    val min: Long = 0,
    val max: Long = 65535,
    val level: TunnelParamLevel = TunnelParamLevel.MAIN,
    val serverDefault: String? = null,
) {
    fun defaultFor(type: TunnelType): String? =
        if (serverDefault != null && TunnelParams.readsAsServer(type)) serverDefault else default
}

/**
 * Every parameter ClientContext::ReadTunnels reads for a tunnel type, with the defaults of the i2pd
 * code (TunnelSourcesTest checks the list against the sources of the submodule). Parameters the form
 * does not know are kept and shown as "other parameters"; keys i2pd does not read are reported.
 */
object TunnelParams {
    private fun text(key: String, required: Boolean = false, default: String? = null, level: TunnelParamLevel = TunnelParamLevel.MAIN) =
        TunnelParam(key, TunnelParamKind.TEXT, required, default, level = level)
    private fun port(key: String, required: Boolean = false, default: String? = null, min: Long = 1, level: TunnelParamLevel = TunnelParamLevel.MAIN) =
        TunnelParam(key, TunnelParamKind.PORT, required, default, min = min, level = level)
    private fun bool(key: String, default: String, level: TunnelParamLevel = TunnelParamLevel.MAIN, serverDefault: String? = null) =
        TunnelParam(key, TunnelParamKind.BOOL, default = default, level = level, serverDefault = serverDefault)
    private fun number(key: String, default: String, min: Long, max: Long, level: TunnelParamLevel = TunnelParamLevel.MAIN) =
        TunnelParam(key, TunnelParamKind.NUMBER, default = default, min = min, max = max, level = level)

    private val ADV = TunnelParamLevel.ADVANCED
    private const val UNLIMITED = Int.MAX_VALUE.toLong()

    private val keysTransient = TunnelParam("keys", TunnelParamKind.KEYS, default = "transient")
    private val keysRequired = TunnelParam("keys", TunnelParamKind.KEYS, required = true)
    private val signatureType = TunnelParam("signaturetype", TunnelParamKind.SIGNATURE_TYPE, default = "7", level = ADV)
    private val cryptoType = text("cryptotype", default = "0", level = ADV)

    // Client tunnels: socks and httpproxy read the same section fields as client
    private val clientAdvanced = listOf(
        signatureType,
        cryptoType,
        bool("matchtunnels", "false", ADV),
    )
    private val tcpClientAdvanced = listOf(
        number("connecttimeout", "0", 0, UNLIMITED, ADV),
        number("i2cp.closeIdleTime", "0", 0, UNLIMITED, ADV),
        bool("i2cp.newDestOnResume", "false", ADV),
    )
    private val keepAlive = number("keepaliveinterval", "0", 0, UNLIMITED, ADV)
    private val maxWindow = number("maxwindow", "1000", 500, 8192, ADV)

    /** Parameters of [type], main and additional, I2CP options apart. */
    fun params(type: TunnelType): List<TunnelParam> = when (type) {
        TunnelType.CLIENT -> listOf(
            TunnelParam("destination", TunnelParamKind.DESTINATION, required = true),
            port("destinationport", default = "0", min = 0),
            text("address", default = "127.0.0.1"),
            port("port", required = true),
            keysTransient,
        ) + clientAdvanced + keepAlive + tcpClientAdvanced
        TunnelType.UDP_CLIENT -> listOf(
            TunnelParam("destination", TunnelParamKind.DESTINATION, required = true),
            port("destinationport", default = "0", min = 0),
            text("address", default = "127.0.0.1"),
            port("port", required = true),
            keysTransient,
        ) + clientAdvanced + listOf(
            keepAlive,
            bool("gzip", "true", ADV),
            number("datagramversion", "3", 1, 3, ADV),
            maxWindow,
        )
        TunnelType.SOCKS -> listOf(
            text("address", default = "127.0.0.1"),
            port("port", required = true),
            keysTransient,
            text("outproxy", default = ""),
            port("destinationport", default = "0", min = 0, level = ADV),
        ) + clientAdvanced + tcpClientAdvanced
        TunnelType.HTTP_PROXY -> listOf(
            text("address", default = "127.0.0.1"),
            port("port", required = true),
            keysTransient,
            text("outproxy", default = ""),
            bool("addresshelper", "true"),
            bool("senduseragent", "false"),
        ) + clientAdvanced + tcpClientAdvanced
        TunnelType.SERVER, TunnelType.HTTP, TunnelType.IRC, TunnelType.UDP_SERVER -> buildList {
            add(text("host", required = true))
            add(port("port", required = true))
            add(keysRequired)
            add(port("inport", min = 0))
            add(text("accesslist", default = ""))
            if (type == TunnelType.HTTP) {
                add(text("hostoverride", default = ""))
                add(bool("i2pheaders", "true"))
            }
            if (type == TunnelType.IRC) add(text("webircpassword", default = ""))
            if (type != TunnelType.UDP_SERVER) add(bool("ssl", "false"))
            add(signatureType)
            add(cryptoType)
            add(bool("gzip", "false", ADV))
            add(text("address", default = "", level = ADV))
            add(bool("enableuniquelocal", "true", ADV))
            if (type == TunnelType.UDP_SERVER) add(maxWindow)
        }
        // Not started by i2pd any more, read like a client tunnel
        TunnelType.WEBSOCKS -> listOf(text("address", default = "127.0.0.1"), port("port", required = true), keysTransient) + clientAdvanced
        TunnelType.TORRENTS -> listOf(
            text("torrentsdir", required = true),
            keysTransient,
            text("trackers", default = ""),
            bool("dht", "true"),
            // RPC server only with a port; "web" serves the files of <datadir>/torrents/web through it
            port("rpcport", default = "0", min = 0),
            text("rpcaddress", default = "127.0.0.1"),
            bool("rpcauth", "false"),
            text("rpcuser", default = ""),
            text("rpcpass", default = ""),
            bool("web", "true"),
            signatureType,
            text("dhtfile", default = "", level = ADV),
            text("rpcpath", default = "", level = ADV),
        )
    }

    fun main(type: TunnelType): List<TunnelParam> = params(type).filter { it.level == TunnelParamLevel.MAIN }

    fun advanced(type: TunnelType): List<TunnelParam> = params(type).filter { it.level == TunnelParamLevel.ADVANCED }

    /** I2CP options read for every tunnel (ClientContext::ReadI2CPOptions), most common ones in the main part. */
    val i2cp: List<TunnelParam> = listOf(
        number("inbound.length", "3", 0, 8),
        number("inbound.quantity", "5", 1, 16),
        number("outbound.length", "3", 0, 8),
        number("outbound.quantity", "5", 1, 16),
        number("inbound.lengthVariance", "0", -3, 3),
        number("outbound.lengthVariance", "0", -3, 3),
        text("i2cp.leaseSetType", default = "3"),
        TunnelParam("i2cp.leaseSetEncType", TunnelParamKind.TEXT, default = "6,4,0", serverDefault = "6,4"),
        number("i2cp.leaseSetAuthType", "0", 0, 2, ADV),
        text("i2cp.leaseSetPrivKey", default = "", level = ADV),
        text("explicitPeers", default = "", level = ADV),
        text("trustedRouters", default = "", level = ADV),
        text("inbound.randomKey", default = "", level = ADV),
        text("outbound.randomKey", default = "", level = ADV),
        number("latency.min", "0", 0, UNLIMITED, ADV),
        number("latency.max", "0", 0, UNLIMITED, ADV),
        number("crypto.tagsToSend", "40", 1, UNLIMITED, ADV),
        text("crypto.ratchet.inboundTags", default = "", level = ADV),
        number("i2p.streaming.initialAckDelay", "200", 0, UNLIMITED, ADV),
        number("i2p.streaming.maxOutboundSpeed", "1730000000", 0, UNLIMITED, ADV),
        number("i2p.streaming.maxInboundSpeed", "1730000000", 0, UNLIMITED, ADV),
        number("i2p.streaming.maxConcurrentStreams", "2048", 0, UNLIMITED, ADV),
        number("i2p.streaming.maxConnsPerMinute", "0", 0, UNLIMITED, ADV),
        bool("i2p.streaming.answerPings", "false", ADV, serverDefault = "true"),
        bool("i2p.streaming.dontSign", "false", ADV),
        number("i2p.streaming.profile", "1", 1, 2, ADV),
        number("i2p.streaming.maxWindowSize", "512", 1, UNLIMITED, ADV),
        number("i2p.streaming.maxResends", "10", 1, UNLIMITED, ADV),
    )

    /** Client authorization keys of an encrypted lease set: i2cp.leaseSetClient.dh.<n> and .psk.<n>. */
    val I2CP_GROUPS = listOf("i2cp.leaseSetClient.dh", "i2cp.leaseSetClient.psk")

    /** Read by i2pd, not shown in the form: old name of accesslist, used when accesslist is empty. */
    private val HIDDEN_SERVER = setOf("whitelist")

    /** ReadI2CPOptions is called with isServer for server tunnels and torrents. */
    fun readsAsServer(type: TunnelType): Boolean = type.kind == TunnelType.Kind.SERVER || type == TunnelType.TORRENTS

    /** Keys handled by the form of [type]; "type" itself is not editable. */
    fun formKeys(type: TunnelType): Set<String> = (params(type) + i2cp).map { it.key }.toSet() + "type"

    /** Whether i2pd reads [key] for a tunnel of [type]. */
    fun isRead(type: TunnelType, key: String): Boolean =
        key in formKeys(type) || I2CP_GROUPS.any { key.startsWith(it) } ||
            (type.kind == TunnelType.Kind.SERVER && key in HIDDEN_SERVER)

    /** Every key read for some tunnel type, group prefixes included: compared with the i2pd sources. */
    val allKeys: Set<String>
        get() = TunnelType.entries.flatMap { formKeys(it) }.toSet() + I2CP_GROUPS + HIDDEN_SERVER

    /**
     * Signature types i2pd generates keys for: RSA (4..6) and EdDSA-ph (8) silently become DSA (0).
     */
    val SIGNATURE_TYPES = setOf(0L, 1L, 2L, 3L, 7L, 9L, 10L, 11L)
}

/**
 * A tunnel section of a file.
 *
 * Tunnels with the same persistent keys share one destination in the whole router (see [SharedDestinations]).
 * Inside one file the first of them sets the I2CP options: later ones take the destination as is.
 */
data class TunnelInfo(
    val name: String,
    val typeName: String?,
    val values: Map<String, String>,
    /** Earlier tunnel of the same file with the same keys: its I2CP options apply, the ones of this tunnel are ignored. */
    val sharesDestinationWith: String?,
    /** Later tunnels of the same file taking the destination (and I2CP options) of this one. */
    val dependents: List<String>,
    /** Line of the section header, to order tunnels with commented ones as in the file. */
    val line: Int = 0,
) {
    val type: TunnelType? get() = TunnelType.of(typeName)
    val keys: String? get() = values["keys"]
}

/** A tunnel switched off by commenting out its lines ("#[name]", "#type = client", ...). */
data class CommentedTunnel(val name: String, val values: Map<String, String>, val line: Int) {
    val type: TunnelType? get() = TunnelType.of(values["type"])
}

sealed interface TunnelIssue {
    val file: String

    data class InvalidLine(override val file: String, val line: Int, val text: String) : TunnelIssue
    data class EntryOutsideSection(override val file: String, val key: String) : TunnelIssue
    data class DuplicateSection(override val file: String, val section: String) : TunnelIssue
    data class DuplicateKey(override val file: String, val section: String, val key: String) : TunnelIssue
    data class MissingType(override val file: String, val section: String) : TunnelIssue
    data class UnknownType(override val file: String, val section: String, val type: String) : TunnelIssue
    data class MissingParam(override val file: String, val section: String, val key: String) : TunnelIssue
    data class InvalidValue(override val file: String, val section: String, val key: String, val value: String, val error: ValueError) : TunnelIssue

    /** Several enabled tunnels (or main config services) listen on one local port. */
    data class PortConflict(override val file: String, val section: String, val port: Int, val others: List<String>) : TunnelIssue

    /** A key i2pd does not read for this tunnel type: a typo or an option of another type, ignored. */
    data class UnreadKey(override val file: String, val section: String, val key: String) : TunnelIssue
}

/**
 * One tunnel file: tunnels.conf or tunnels.d/name.conf; a ".disabled" suffix switches the whole file off,
 * since tunnels sharing keys depend on their order in the file.
 */
class TunnelFile(val path: String, val document: IniDocument) {
    val enabled: Boolean get() = path.endsWith(".conf")

    val tunnels: List<TunnelInfo>
        get() {
            val byName = linkedMapOf<String, MutableMap<String, String>>()
            for (entry in document.entries) {
                if (entry.section.isEmpty()) continue
                byName.getOrPut(entry.section) { linkedMapOf() }.putIfAbsent(entry.key, entry.value)
            }
            document.sections.forEach { byName.getOrPut(it) { linkedMapOf() } }
            val firstWithKeys = mutableMapOf<String, String>()
            val dependents = mutableMapOf<String, MutableList<String>>()
            val shares = mutableMapOf<String, String>()
            for ((name, values) in byName) {
                val keys = values["keys"]?.takeIf(::isSharedKeys) ?: continue
                val primary = firstWithKeys.putIfAbsent(keys, name)
                if (primary != null) {
                    shares[name] = primary
                    dependents.getOrPut(primary) { mutableListOf() } += name
                }
            }
            return byName.map { (name, values) ->
                TunnelInfo(name, values["type"], values, shares[name], dependents[name].orEmpty(), document.sectionLine(name))
            }
        }

    /** Tunnels switched off by commenting out their section; i2pd does not see them. */
    val commentedTunnels: List<CommentedTunnel>
        get() = document.commentedSections.map { section ->
            CommentedTunnel(section.name, section.entries.groupBy({ it.first }, { it.second }).mapValues { it.value.first() }, section.lines.first)
        }

    /** Names of all tunnels of the file, commented ones included: a new tunnel must not repeat them. */
    val allNames: Set<String> get() = tunnels.map { it.name }.toSet() + commentedTunnels.map { it.name }

    /** Switches tunnel [name] off by commenting out its section, which keeps the order of the file. */
    fun commentOut(name: String): Boolean = document.commentOutSection(name)

    /** Switches the commented tunnel [name] on again (the first one with this name). */
    fun uncomment(name: String): Boolean {
        val section = document.commentedSections.firstOrNull { it.name == name } ?: return false
        document.uncommentSection(section)
        return true
    }

    operator fun get(name: String): TunnelInfo? = tunnels.firstOrNull { it.name == name }

    fun set(tunnel: String, key: String, value: String) = document.set(tunnel, key, value)

    fun remove(tunnel: String, key: String) = document.remove(tunnel, key)

    /** Problems i2pd would report for this file alone. */
    fun validate(): List<TunnelIssue> {
        val issues = mutableListOf<TunnelIssue>()
        val text = document.toString().split("\n")
        document.invalidLines().forEach { issues += TunnelIssue.InvalidLine(path, it, text.getOrElse(it) { "" }.trim()) }
        val seenSections = mutableSetOf<String>()
        val seenKeys = mutableSetOf<Pair<String, String>>()
        var lastSection: String? = null
        for (entry in document.entries) {
            if (entry.section.isEmpty()) {
                issues += TunnelIssue.EntryOutsideSection(path, entry.key)
                continue
            }
            if (entry.section != lastSection && !seenSections.add(entry.section)) issues += TunnelIssue.DuplicateSection(path, entry.section)
            lastSection = entry.section
            if (!seenKeys.add(entry.section to entry.key)) issues += TunnelIssue.DuplicateKey(path, entry.section, entry.key)
        }
        for (tunnel in tunnels) {
            val type = tunnel.type
            when {
                tunnel.typeName == null -> issues += TunnelIssue.MissingType(path, tunnel.name)
                type == null -> issues += TunnelIssue.UnknownType(path, tunnel.name, tunnel.typeName)
                else -> {
                    for (param in TunnelParams.params(type) + TunnelParams.i2cp) {
                        val value = tunnel.values[param.key]
                        if (value == null) {
                            if (param.required) issues += TunnelIssue.MissingParam(path, tunnel.name, param.key)
                            continue
                        }
                        validateParam(param, value)?.let { issues += TunnelIssue.InvalidValue(path, tunnel.name, param.key, value, it) }
                    }
                    tunnel.values.keys.filterNot { TunnelParams.isRead(type, it) }
                        .forEach { issues += TunnelIssue.UnreadKey(path, tunnel.name, it) }
                }
            }
        }
        return issues.distinct()
    }

    companion object {
        /** Keys that create a persistent destination shared by later tunnels of the same file. */
        fun isSharedKeys(keys: String): Boolean = keys.isNotBlank() && !keys.startsWith("transient") && keys != "shareddest"

        fun parse(path: String, text: String) = TunnelFile(path, IniDocument.parse(text, IniDialect.PROPERTY_TREE))

        fun validateParam(param: TunnelParam, value: String): ValueError? = when (param.kind) {
            TunnelParamKind.PORT -> {
                val port = value.toLongOrNull()
                when {
                    port == null -> ValueError.NOT_NUMBER
                    port !in param.min..param.max -> ValueError.OUT_OF_RANGE
                    else -> null
                }
            }
            TunnelParamKind.NUMBER -> {
                val number = value.toLongOrNull()
                when {
                    number == null -> ValueError.NOT_NUMBER
                    number !in param.min..param.max -> ValueError.OUT_OF_RANGE
                    else -> null
                }
            }
            TunnelParamKind.BOOL -> if (value.lowercase() in setOf("true", "false", "1", "0")) null else ValueError.NOT_BOOLEAN
            TunnelParamKind.SIGNATURE_TYPE -> when (value.toLongOrNull()) {
                null -> ValueError.NOT_NUMBER
                in TunnelParams.SIGNATURE_TYPES -> null
                else -> ValueError.NOT_ALLOWED
            }
            TunnelParamKind.TEXT, TunnelParamKind.DESTINATION, TunnelParamKind.KEYS -> null
        }

        /**
         * Local ports of enabled client tunnels and torrents RPC servers used more than once, among
         * tunnels and the given main config services (name to port).
         */
        fun portConflicts(files: List<TunnelFile>, services: Map<String, Int>): List<TunnelIssue.PortConflict> {
            val listeners = mutableMapOf<Int, MutableList<Pair<String?, String>>>() // port -> (file, name)
            services.forEach { (name, port) -> listeners.getOrPut(port) { mutableListOf() } += null to name }
            for (file in files.filter { it.enabled }) {
                for (tunnel in file.tunnels) {
                    val port = when {
                        tunnel.type?.kind == TunnelType.Kind.CLIENT -> tunnel.values["port"]?.toIntOrNull()
                        tunnel.type == TunnelType.TORRENTS -> tunnel.values["rpcport"]?.toIntOrNull()?.takeIf { it > 0 }
                        else -> null
                    } ?: continue
                    listeners.getOrPut(port) { mutableListOf() } += file.path to tunnel.name
                }
            }
            return listeners.filterValues { it.size > 1 }.flatMap { (port, owners) ->
                owners.filter { it.first != null }.map { (file, name) ->
                    TunnelIssue.PortConflict(file!!, name, port, owners.filter { it.second != name || it.first != file }.map { it.second })
                }
            }
        }
    }
}

/** I2P addresses of destinations, from i2pd key files. */
object I2pAddress {
    private const val BASE32 = "abcdefghijklmnopqrstuvwxyz234567"

    /**
     * Address of the destination stored in a private keys file: base32 of SHA-256 of its public identity
     * (256 bytes of encryption key + 128 bytes of signing key + certificate). Null if the data is too short.
     */
    fun b32FromKeys(keys: ByteArray): String? {
        if (keys.size < 387) return null
        val certLength = ((keys[385].toInt() and 0xff) shl 8) or (keys[386].toInt() and 0xff)
        val identityLength = 387 + certLength
        if (keys.size < identityLength) return null
        val hash = MessageDigest.getInstance("SHA-256").digest(keys.copyOfRange(0, identityLength))
        return base32(hash) + ".b32.i2p"
    }

    /**
     * Address of a destination publishing an encrypted lease set (i2cp.leaseSetType = 5):
     * BlindedPublicKey::ToB33 of i2pd. Only for EdDSA (7) and RedDSA (11) keys, null otherwise.
     * [clientAuth] is set when lease set authorization is enabled (i2cp.leaseSetAuthType > 0).
     */
    fun b33FromKeys(keys: ByteArray, clientAuth: Boolean = false): String? {
        if (keys.size < 391 || keys[384].toInt() != KEY_CERTIFICATE) return null
        val sigType = ((keys[387].toInt() and 0xff) shl 8) or (keys[388].toInt() and 0xff)
        if (sigType != SIG_EDDSA && sigType != SIG_REDDSA) return null
        // 32 byte signing key at the end of the 128 byte signing key field
        val publicKey = keys.copyOfRange(384 - 32, 384)
        val address = ByteArray(3 + publicKey.size)
        address[0] = if (clientAuth) PER_CLIENT_AUTH_FLAG else 0
        address[1] = sigType.toByte()
        address[2] = SIG_REDDSA.toByte() // blinded signature type is always RedDSA
        publicKey.copyInto(address, 3)
        val checksum = CRC32().apply { update(publicKey) }.value
        address[0] = (address[0].toLong() xor (checksum and 0xff)).toByte()
        address[1] = (address[1].toLong() xor ((checksum shr 8) and 0xff)).toByte()
        address[2] = (address[2].toLong() xor ((checksum shr 16) and 0xff)).toByte()
        return base32(address) + ".b32.i2p"
    }

    private const val KEY_CERTIFICATE = 5
    private const val SIG_EDDSA = 7
    private const val SIG_REDDSA = 11
    private const val PER_CLIENT_AUTH_FLAG: Byte = 0x04

    fun base32(data: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                append(BASE32[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) append(BASE32[(buffer shl (5 - bits)) and 31])
    }
}

/** Something using a destination: a tunnel of a file, or a proxy of i2pd.conf when [file] is null. */
data class DestinationUser(val file: String?, val name: String)

/**
 * Destinations shared by several tunnels or proxies. i2pd finds a running destination by the identity
 * of its keys anywhere in the router and reconfigures it with the I2CP options of each file reading it,
 * so all users of one key file affect each other.
 */
object SharedDestinations {
    /**
     * Key file to its users in i2pd reading order: proxies of i2pd.conf, tunnels.conf, then tunnels.d
     * (sorted by name here; i2pd reads that directory in file system order). Only keys used more than once.
     */
    fun of(files: List<TunnelFile>, proxyKeys: Map<String, String>): Map<String, List<DestinationUser>> {
        val users = linkedMapOf<String, MutableList<DestinationUser>>()
        proxyKeys.forEach { (proxy, keys) ->
            if (TunnelFile.isSharedKeys(keys)) users.getOrPut(keys) { mutableListOf() } += DestinationUser(null, proxy)
        }
        val ordered = files.filter { it.enabled }.sortedWith(compareBy({ it.path != "tunnels.conf" }, { it.path }))
        for (file in ordered) {
            for (tunnel in file.tunnels) {
                val keys = tunnel.keys?.takeIf(TunnelFile::isSharedKeys) ?: continue
                users.getOrPut(keys) { mutableListOf() } += DestinationUser(file.path, tunnel.name)
            }
        }
        return users.filterValues { it.size > 1 }
    }
}
