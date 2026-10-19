package org.purplei2p.i2pd.config

enum class OptionType { BOOL, STRING, UINT16, UINT32, UINT64, INT, DOUBLE }

/** One i2pd option as described by the native library (i2p::config::m_OptionsDesc). */
data class OptionSpec(
    /** Full name, e.g. "http.port" or "bandwidth". */
    val name: String,
    val type: OptionType,
    val default: String?,
    val description: String,
) {
    /** Section in i2pd.conf: the part before the first dot, "" for global options. */
    val section: String get() = if ('.' in name) name.substringBefore('.') else ""

    /** Key inside [section]. */
    val key: String get() = if ('.' in name) name.substringAfter('.') else name
}

/** All options of the i2pd build in this APK. */
class OptionSchema(val options: List<OptionSpec>) {
    private val byName = options.associateBy { it.name }

    operator fun get(name: String): OptionSpec? = byName[name]

    /** Sections in the order of their first option. */
    val sections: List<String> get() = options.map { it.section }.distinct()

    companion object {
        /**
         * Parses the output of I2pdNative.describeOptions: one option per line,
         * tab separated name, type, has default (0/1), default, description; "\\", "\t", "\n" escaped.
         */
        fun parse(text: String): OptionSchema {
            val options = text.lineSequence().filter { it.isNotEmpty() }.mapNotNull { line ->
                val fields = splitEscaped(line)
                if (fields.size < 5) return@mapNotNull null
                OptionSpec(
                    name = fields[0],
                    type = typeOf(fields[1]),
                    default = if (fields[2] == "1") fields[3] else null,
                    description = fields[4],
                )
            }.toList()
            return OptionSchema(options)
        }

        private fun typeOf(name: String) = when (name) {
            "bool" -> OptionType.BOOL
            "uint16" -> OptionType.UINT16
            "uint32" -> OptionType.UINT32
            "uint64" -> OptionType.UINT64
            "int" -> OptionType.INT
            "double" -> OptionType.DOUBLE
            else -> OptionType.STRING
        }

        private fun splitEscaped(line: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '\\' && i + 1 < line.length -> {
                        when (val next = line[i + 1]) {
                            't' -> current.append('\t')
                            'n' -> current.append('\n')
                            else -> current.append(next)
                        }
                        i++
                    }
                    c == '\t' -> {
                        fields += current.toString()
                        current.clear()
                    }
                    else -> current.append(c)
                }
                i++
            }
            fields += current.toString()
            return fields
        }
    }
}
