package org.purplei2p.i2pd.config

/** Problems that make i2pd refuse the config or misbehave. */
sealed interface ConfigIssue {
    /** i2pd does not know this option and fails to start with it. */
    data class UnknownOption(val name: String, val line: Int) : ConfigIssue

    data class InvalidValue(val name: String, val value: String, val reason: ValueError) : ConfigIssue

    /** The option is set more than once; i2pd fails to start ("multiple occurrences"). */
    data class Duplicate(val name: String) : ConfigIssue

    /** Several enabled local services listen on the same port. */
    data class PortConflict(val port: Int, val options: List<String>) : ConfigIssue
}

/** Why a value is rejected; a [warning] is accepted by i2pd but needs confirmation from the user. */
/** Effective value of an option before and after an edit; null means not set and without default. */
data class OptionChange(val name: String, val before: String?, val after: String?)

enum class ValueError(val warning: Boolean = false) {
    NOT_BOOLEAN,
    NOT_NUMBER,
    OUT_OF_RANGE,
    NOT_ALLOWED,

    /** Router port outside the range i2pd picks from itself. */
    OUTSIDE_RECOMMENDED_RANGE(warning = true),

    /** Router port used by well known software. */
    RESERVED_PORT(warning = true),
}

/**
 * i2pd.conf seen through the option schema. An option "a.b" may be written as "b" in section [a]
 * or as "a.b" in the global section; both forms are recognized.
 */
class ConfigEditor(val document: IniDocument, val schema: OptionSchema) {

    private fun optionName(entry: IniDocument.EntryRef) =
        if (entry.section.isEmpty()) entry.key else "${entry.section}.${entry.key}"

    private fun entriesOf(name: String) = document.entries.filter { optionName(it) == name }

    /** Value written in the file, null if the option is not set. */
    fun valueOf(name: String): String? = entriesOf(name).firstOrNull()?.value

    fun isSet(name: String): Boolean = entriesOf(name).isNotEmpty()

    /** Value i2pd will use: from the file or the default. */
    fun effectiveValue(name: String): String? = valueOf(name) ?: schema[name]?.default

    /** Writes [value]; an option that is not in the file goes to its section. */
    fun set(name: String, value: String) {
        val existing = entriesOf(name).firstOrNull()
        if (existing != null) {
            document.setLine(existing.line, value)
        } else {
            val spec = schema[name]
            val section = spec?.section ?: (if ('.' in name) name.substringBefore('.') else "")
            val key = spec?.key ?: (if ('.' in name) name.substringAfter('.') else name)
            document.set(section, key, value)
        }
    }

    /** Removes the option from the file, so i2pd uses its default. */
    fun reset(name: String) {
        document.removeLines(entriesOf(name).map { it.line })
    }

    /** Entries with options unknown to this i2pd build. */
    fun unknownEntries(): List<IniDocument.EntryRef> = document.entries.filter { schema[optionName(it)] == null }

    fun removeEntry(entry: IniDocument.EntryRef, commentOut: Boolean) {
        document.removeLines(listOf(entry.line), commentOut)
    }

    /**
     * Options whose effective values differ from [before] (defaults included), so formatting,
     * comments or an explicitly written default value are not changes. Unknown options compare by text.
     */
    fun changesSince(before: ConfigEditor): List<OptionChange> {
        val changes = schema.options.mapNotNull { spec ->
            val old = before.effectiveValue(spec.name)
            val new = effectiveValue(spec.name)
            if (sameValue(spec, old, new)) null else OptionChange(spec.name, old, new)
        }
        val unknownBefore = before.unknownEntries().associate { optionName(it) to it.value }
        val unknownNow = unknownEntries().associate { optionName(it) to it.value }
        val unknownChanges = (unknownBefore.keys + unknownNow.keys).filter { unknownBefore[it] != unknownNow[it] }
            .map { OptionChange(it, unknownBefore[it], unknownNow[it]) }
        return changes + unknownChanges
    }

    private fun sameValue(spec: OptionSpec, a: String?, b: String?): Boolean = when {
        a == b -> true
        a == null || b == null -> false
        spec.type == OptionType.BOOL -> parseBool(a) == parseBool(b)
        spec.type == OptionType.DOUBLE -> a.toDoubleOrNull() != null && a.toDoubleOrNull() == b.toDoubleOrNull()
        spec.type != OptionType.STRING -> a.toLongOrNull() != null && a.toLongOrNull() == b.toLongOrNull()
        else -> false
    }

    fun validate(): List<ConfigIssue> {
        val issues = mutableListOf<ConfigIssue>()
        val seen = mutableSetOf<String>()
        for (entry in document.entries) {
            val name = optionName(entry)
            val spec = schema[name]
            if (spec == null) {
                issues += ConfigIssue.UnknownOption(name, entry.line)
                continue
            }
            if (!seen.add(name)) issues += ConfigIssue.Duplicate(name)
            validateValue(spec, entry.value)?.let { issues += ConfigIssue.InvalidValue(name, entry.value, it) }
        }
        issues += portConflicts()
        return issues.distinct()
    }

    /** Checks a value against the option type and its widget limits; null if it is fine. */
    fun validateValue(spec: OptionSpec, value: String): ValueError? = validateValue(spec, OptionOverlay.uiFor(spec).widget, value)

    private fun portConflicts(): List<ConfigIssue.PortConflict> {
        val ports = mutableMapOf<Int, MutableList<String>>()
        for (service in LOCAL_SERVICES) {
            if (schema["$service.enabled"] == null || schema["$service.port"] == null) continue
            if (!parseBool(effectiveValue("$service.enabled"))) continue
            val port = effectiveValue("$service.port")?.toIntOrNull() ?: continue
            ports.getOrPut(port) { mutableListOf() } += "$service.port"
        }
        return ports.filterValues { it.size > 1 }.map { (port, options) -> ConfigIssue.PortConflict(port, options) }
    }

    companion object {
        /** Sections with "enabled" and "port" options of services listening locally. */
        private val LOCAL_SERVICES = listOf("http", "httpproxy", "socksproxy", "sam", "bob", "i2cp", "i2pcontrol")

        private val TRUE = setOf("true", "1", "yes", "on")
        private val FALSE = setOf("false", "0", "no", "off")

        fun parseBool(value: String?): Boolean = value?.lowercase() in TRUE

        fun validateValue(spec: OptionSpec, widget: OptionWidget, value: String): ValueError? {
            when (spec.type) {
                OptionType.BOOL -> {
                    val lower = value.lowercase()
                    if (lower !in TRUE && lower !in FALSE) return ValueError.NOT_BOOLEAN
                }
                OptionType.UINT16, OptionType.UINT32, OptionType.UINT64, OptionType.INT -> {
                    val number = value.toLongOrNull() ?: return ValueError.NOT_NUMBER
                    val (min, max) = when (spec.type) {
                        OptionType.UINT16 -> 0L to 65535L
                        OptionType.UINT32 -> 0L to 4294967295L
                        OptionType.UINT64 -> 0L to Long.MAX_VALUE
                        else -> Int.MIN_VALUE.toLong() to Int.MAX_VALUE.toLong()
                    }
                    if (number !in min..max) return ValueError.OUT_OF_RANGE
                }
                OptionType.DOUBLE -> value.toDoubleOrNull() ?: return ValueError.NOT_NUMBER
                OptionType.STRING -> Unit
            }
            return when (widget) {
                OptionWidget.Port -> if (value.toLongOrNull() in 1L..65535L) null else ValueError.OUT_OF_RANGE
                OptionWidget.RouterPort -> {
                    val port = value.toIntOrNull()
                    when {
                        port == 0 -> null // auto
                        port == null -> ValueError.NOT_NUMBER
                        port !in RouterPorts.RANGE -> ValueError.OUTSIDE_RECOMMENDED_RANGE
                        port in RouterPorts.RESERVED -> ValueError.RESERVED_PORT
                        else -> null
                    }
                }
                is OptionWidget.Number -> if ((value.toLongOrNull() ?: return null) in widget.min..widget.max) null else ValueError.OUT_OF_RANGE
                is OptionWidget.Choice -> if (widget.allowCustom || value in widget.values) null else ValueError.NOT_ALLOWED
                OptionWidget.Switch, OptionWidget.Text -> null
            }
        }
    }
}
