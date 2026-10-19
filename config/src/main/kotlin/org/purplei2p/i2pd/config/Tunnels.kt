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

    /** Needs a directory for torrents, so it is edited as is, not created from the form. */
    TORRENTS("torrents", Kind.OTHER, creatable = false);

    enum class Kind { CLIENT, SERVER, OTHER }

    companion object {
        fun of(id: String?): TunnelType? = entries.firstOrNull { it.id == id }
    }
}

enum class TunnelParamKind { TEXT, PORT, BOOL, NUMBER, DESTINATION, KEYS, SIGNATURE_TYPE }

/** A tunnel parameter with the default i2pd uses when it is not set. */
data class TunnelParam(
    val key: String,
    val kind: TunnelParamKind,
    val required: Boolean = false,
    val default: String? = null,
    val min: Long = 0,
    val max: Long = 65535,
)

/** Parameters shown in tunnel forms; everything else is kept and shown as "other parameters". */
object TunnelParams {
    private fun text(key: String, required: Boolean = false, default: String? = null) = TunnelParam(key, TunnelParamKind.TEXT, required, default)
    private fun port(key: String, required: Boolean = false, default: String? = null, min: Long = 1) =
        TunnelParam(key, TunnelParamKind.PORT, required, default, min = min)
    private fun bool(key: String, default: String) = TunnelParam(key, TunnelParamKind.BOOL, default = default)
    private fun number(key: String, default: String, min: Long, max: Long) = TunnelParam(key, TunnelParamKind.NUMBER, default = default, min = min, max = max)

    private val keysTransient = TunnelParam("keys", TunnelParamKind.KEYS, default = "transient")
    private val keysRequired = TunnelParam("keys", TunnelParamKind.KEYS, required = true)

    fun main(type: TunnelType): List<TunnelParam> = when (type) {
        TunnelType.CLIENT, TunnelType.UDP_CLIENT -> listOf(
            TunnelParam("destination", TunnelParamKind.DESTINATION, required = true),
            port("destinationport", default = "0", min = 0),
            text("address", default = "127.0.0.1"),
            port("port", required = true),
            keysTransient,
        )
        TunnelType.SOCKS -> listOf(text("address", default = "127.0.0.1"), port("port", required = true), keysTransient, text("outproxy", default = ""))
        TunnelType.HTTP_PROXY -> listOf(
            text("address", default = "127.0.0.1"),
            port("port", required = true),
            keysTransient,
            text("outproxy", default = ""),
            bool("addresshelper", "true"),
            bool("senduseragent", "false"),
        )
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
        }
        TunnelType.WEBSOCKS -> listOf(text("address", default = "127.0.0.1"), port("port", required = true), keysTransient)
        TunnelType.TORRENTS -> listOf(text("torrentsdir", required = true), keysTransient, text("trackers", default = ""))
    }

    /** I2CP options read for every tunnel (ClientContext::ReadI2CPOptions), most common first. */
    val i2cp: List<TunnelParam> = listOf(
        number("inbound.length", "3", 0, 8),
        number("inbound.quantity", "5", 1, 16),
        number("outbound.length", "3", 0, 8),
        number("outbound.quantity", "5", 1, 16),
        number("inbound.lengthVariance", "0", -3, 3),
        number("outbound.lengthVariance", "0", -3, 3),
        text("i2cp.leaseSetType", default = "3"),
        text("i2cp.leaseSetEncType", default = ""),
        bool("i2cp.dontPublishLeaseSet", "false"),
    )

    /**
     * Signature types i2pd generates keys for: RSA (4..6) and EdDSA-ph (8) silently become DSA (0).
     * Checked although not in the form.
     */
    val SIGNATURE_TYPES = setOf(0L, 1L, 2L, 3L, 7L, 9L, 10L, 11L)
    val checkedOther: List<TunnelParam> = listOf(TunnelParam("signaturetype", TunnelParamKind.SIGNATURE_TYPE))

    /** Keys handled by the form of [type]; "type" itself is not editable. */
    fun formKeys(type: TunnelType): Set<String> = (main(type) + i2cp).map { it.key }.toSet() + "type"
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
) {
    val type: TunnelType? get() = TunnelType.of(typeName)
    val keys: String? get() = values["keys"]
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
                TunnelInfo(name, values["type"], values, shares[name], dependents[name].orEmpty())
            }
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
                else -> for (param in TunnelParams.main(type) + TunnelParams.i2cp + TunnelParams.checkedOther) {
                    val value = tunnel.values[param.key]
                    if (value == null) {
                        if (param.required) issues += TunnelIssue.MissingParam(path, tunnel.name, param.key)
                        continue
                    }
                    validateParam(param, value)?.let { issues += TunnelIssue.InvalidValue(path, tunnel.name, param.key, value, it) }
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
         * Local ports of enabled client tunnels used more than once, among tunnels and the given
         * main config services (name to port).
         */
        fun portConflicts(files: List<TunnelFile>, services: Map<String, Int>): List<TunnelIssue.PortConflict> {
            val listeners = mutableMapOf<Int, MutableList<Pair<String?, String>>>() // port -> (file, name)
            services.forEach { (name, port) -> listeners.getOrPut(port) { mutableListOf() } += null to name }
            for (file in files.filter { it.enabled }) {
                for (tunnel in file.tunnels) {
                    if (tunnel.type?.kind != TunnelType.Kind.CLIENT) continue
                    val port = tunnel.values["port"]?.toIntOrNull() ?: continue
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
