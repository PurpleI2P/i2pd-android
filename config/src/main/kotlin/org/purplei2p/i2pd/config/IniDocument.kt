package org.purplei2p.i2pd.config

/** The two INI flavours i2pd reads. */
enum class IniDialect {
    /** i2pd.conf, boost::program_options: "#" starts a comment anywhere in a line. */
    PROGRAM_OPTIONS,

    /**
     * Tunnel files, boost::property_tree::read_ini: only whole lines starting with "#" or ";" are comments,
     * a "#" inside a line belongs to the value; any other line without "=" makes the file invalid.
     */
    PROPERTY_TREE,
}

/**
 * INI file of i2pd, edited without losing anything:
 * comments, blank lines, order, formatting and unknown lines are written back as they were read.
 *
 * Only the changed lines are rewritten. Keys before the first section belong to the global section "".
 */
class IniDocument private constructor(
    private val lines: MutableList<Line>,
    private val lineSeparator: String,
    private val endsWithNewline: Boolean,
    private val bom: Boolean,
    val dialect: IniDialect,
) {
    sealed interface Line {
        val raw: String

        /** Blank line, comment or anything that is not a section or an entry. */
        data class Other(override val raw: String) : Line

        data class Section(override val raw: String, val name: String) : Line

        data class Entry(
            override val raw: String,
            val key: String,
            val value: String,
            /** Text before the key, kept when the line is rewritten. */
            val indent: String,
            /** Comment at the end of the line including "#" and spaces before it, kept when rewritten. */
            val comment: String,
        ) : Line
    }

    /** An entry together with the section it belongs to. */
    data class EntryRef(val section: String, val key: String, val value: String, val line: Int)

    /**
     * A section switched off by commenting out its lines: "#[name]" (or ";[name]") and the comment lines
     * right after it, up to a blank line, a real section or entry, or the next commented header.
     * [entries] are the commented "key = value" lines of the block, [lines] its line indexes.
     */
    data class CommentedSection(val name: String, val lines: IntRange, val entries: List<Pair<String, String>>)

    val entries: List<EntryRef>
        get() {
            val result = mutableListOf<EntryRef>()
            var section = ""
            lines.forEachIndexed { index, line ->
                when (line) {
                    is Line.Section -> section = line.name
                    is Line.Entry -> result += EntryRef(section, line.key, line.value, index)
                    is Line.Other -> Unit
                }
            }
            return result
        }

    /** Section names in file order, without the global section. */
    val sections: List<String>
        get() = lines.filterIsInstance<Line.Section>().map { it.name }.distinct()

    fun get(section: String, key: String): String? = entries.firstOrNull { it.section == section && it.key == key }?.value

    /** Changes the value of the first matching entry, or adds the entry to the end of the section. */
    fun set(section: String, key: String, value: String) {
        val existing = entries.firstOrNull { it.section == section && it.key == key }
        if (existing != null) {
            setLine(existing.line, value)
            return
        }
        val entry = Line.Entry("$key = $value", key, value, indent = "", comment = "")
        val insertAt = insertionPoint(section)
        if (insertAt != null) {
            lines.add(insertAt, entry)
        } else {
            // New section at the end of the file, separated by a blank line
            if (lines.isNotEmpty() && lines.last().raw.isNotBlank()) lines += Line.Other("")
            lines += Line.Section("[$section]", section)
            lines += entry
        }
    }

    /** Changes the value of the entry at [line] (see [EntryRef.line]). */
    fun setLine(line: Int, value: String) {
        val entry = lines[line] as Line.Entry
        lines[line] = entry.copy(raw = "${entry.indent}${entry.key} = $value${entry.comment}", value = value)
    }

    /**
     * Removes all matching entries. With [commentOut] the lines stay as comments ("# key = value"),
     * which keeps the old value visible to people editing the file by hand.
     * Returns the number of removed entries.
     */
    fun remove(section: String, key: String, commentOut: Boolean = false): Int {
        val matching = entries.filter { it.section == section && it.key == key }.map { it.line }
        removeLines(matching, commentOut)
        return matching.size
    }

    /** Removes entries at [lineIndexes] (see [EntryRef.line]). */
    fun removeLines(lineIndexes: Collection<Int>, commentOut: Boolean = false) {
        for (index in lineIndexes.sortedDescending()) {
            val entry = lines[index] as Line.Entry
            if (commentOut) {
                lines[index] = Line.Other("${entry.indent}# ${entry.key} = ${entry.value}${entry.comment}")
            } else {
                lines.removeAt(index)
            }
        }
    }

    /** Index of the header line of [section] (first one if repeated), -1 if absent. */
    fun sectionLine(section: String): Int = lines.indexOfFirst { it is Line.Section && it.name == section }

    /** Lines of [section]: its header and everything up to the next header. */
    private fun sectionRange(section: String): IntRange? {
        val start = sectionLine(section)
        if (start < 0) return null
        val next = (start + 1 until lines.size).firstOrNull { lines[it] is Line.Section } ?: lines.size
        return start until next
    }

    /** Raw text of [section] with its header, as in the file. */
    fun sectionText(section: String): String? = sectionRange(section)?.joinToString("\n") { lines[it].raw }

    /** Removes [section] with all its lines (comments inside it included). Returns false if there is no such section. */
    fun removeSection(section: String): Boolean {
        val range = sectionRange(section) ?: return false
        // Keep the file tidy: drop the blank line that separated the section from the previous one
        val from = if (range.first > 0 && lines[range.first - 1].raw.isBlank() && range.last == lines.lastIndex) range.first - 1 else range.first
        repeat(range.last - from + 1) { lines.removeAt(from) }
        return true
    }

    fun renameSection(from: String, to: String): Boolean {
        val index = sectionLine(from)
        if (index < 0) return false
        val old = lines[index] as Line.Section
        lines[index] = Line.Section(old.raw.replaceFirst("[$from]", "[$to]").let { if (it == old.raw) "[$to]" else it }, to)
        return true
    }

    /** Appends a new section with [entries] at the end of the file. */
    fun addSection(section: String, entries: List<Pair<String, String>>) {
        if (lines.isNotEmpty() && lines.last().raw.isNotBlank()) lines += Line.Other("")
        lines += Line.Section("[$section]", section)
        entries.forEach { (key, value) -> lines += Line.Entry("$key = $value", key, value, indent = "", comment = "") }
    }

    /** Sections switched off by comments (see [CommentedSection]); only blocks with at least one entry. */
    val commentedSections: List<CommentedSection>
        get() {
            val result = mutableListOf<CommentedSection>()
            var index = 0
            while (index < lines.size) {
                val header = uncommented(lines[index])
                if (header !is Line.Section) {
                    index++
                    continue
                }
                var end = index
                val entries = mutableListOf<Pair<String, String>>()
                while (end + 1 < lines.size) {
                    val line = lines[end + 1]
                    if (line !is Line.Other || line.raw.isBlank()) break
                    val inner = uncommented(line) ?: break
                    if (inner is Line.Section) break
                    if (inner is Line.Entry && isOptionName(inner.key)) entries += inner.key to inner.value
                    end++
                }
                if (entries.isNotEmpty()) result += CommentedSection(header.name, index..end, entries)
                index = end + 1
            }
            return result
        }

    /**
     * Switches [section] off: every line of it gets a "#" in front (a blank one becomes "#", so the block
     * stays in one piece), comments already in it become "##"; [uncommentSection] gives the same text back.
     */
    fun commentOutSection(section: String): Boolean {
        val range = sectionRange(section) ?: return false
        // Comments after the last entry are commented out too ("##"): left as they are, they would join
        // the commented block and be switched on with it. Trailing blank lines stay.
        val last = range.reversed().first { index -> lines[index].raw.isNotBlank() }
        for (index in range.first..last) lines[index] = Line.Other(prefixComment(lines[index].raw))
        return true
    }

    /**
     * Switches a commented section on again: removes one "#" or ";" from the lines of [commented] that
     * become a section, an entry or still a comment. Plain text lines stay comments, so the file never
     * gets a line i2pd would reject.
     */
    fun uncommentSection(commented: CommentedSection) {
        for (index in commented.lines) {
            val inner = uncommented(lines[index]) ?: continue
            // A plain text comment would become a line i2pd rejects, and text with "=" such as a link
            // ("# https://t.me/proxy?server=...") a bogus entry: both stay comments
            if (inner is Line.Other && inner.raw.isNotBlank() && !isComment(inner.raw.trim(), dialect)) continue
            if (inner is Line.Entry && !isOptionName(inner.key)) continue
            lines[index] = inner
        }
    }

    /** [line] without one leading comment marker, parsed; null if it is not a comment. */
    private fun uncommented(line: Line): Line? {
        if (line !is Line.Other) return null
        val trimmed = line.raw.trimStart()
        if (!isComment(trimmed, dialect)) return null
        val indent = line.raw.substring(0, line.raw.length - trimmed.length)
        return parseLine(indent + trimmed.substring(1).trimStart(), dialect)
    }

    /** Option names of i2pd: letters, digits, ".", "_" and "-" ("inbound.length", "i2cp.leaseSetType"). */
    private fun isOptionName(key: String) = key.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }

    private fun prefixComment(raw: String): String {
        val trimmed = raw.trimStart()
        return raw.substring(0, raw.length - trimmed.length) + "#" + trimmed
    }

    /** Indexes of lines the parser of [dialect] would reject: not blank, not a comment, not a section or an entry. */
    fun invalidLines(): List<Int> = lines.indices.filter { index ->
        val line = lines[index]
        line is Line.Other && line.raw.trim().let { it.isNotEmpty() && !isComment(it, dialect) }
    }

    override fun toString(): String = buildString {
        if (bom) append('﻿')
        lines.forEachIndexed { index, line ->
            append(line.raw)
            if (index < lines.lastIndex || endsWithNewline) append(lineSeparator)
        }
    }

    /** Position after the last entry of [section], after its header if it has no entries; null if there is no such section. */
    private fun insertionPoint(section: String): Int? {
        var current = ""
        var sectionSeen = section == ""
        var lastEntry = -1
        var header = -1
        lines.forEachIndexed { index, line ->
            when (line) {
                is Line.Section -> {
                    current = line.name
                    if (current == section && header < 0) {
                        sectionSeen = true
                        header = index
                    }
                }
                is Line.Entry -> if (current == section) lastEntry = index
                is Line.Other -> Unit
            }
        }
        return when {
            !sectionSeen -> null
            lastEntry >= 0 -> lastEntry + 1
            section != "" -> header + 1
            // Global section without entries: before the first section header
            else -> lines.indexOfFirst { it is Line.Section }.takeIf { it >= 0 } ?: lines.size
        }
    }

    companion object {
        fun parse(text: String, dialect: IniDialect = IniDialect.PROGRAM_OPTIONS): IniDocument {
            val bom = text.startsWith('﻿')
            val body = if (bom) text.substring(1) else text
            val separator = if (body.contains("\r\n")) "\r\n" else "\n"
            // An empty document has no lines; lines added to it end with a newline
            val endsWithNewline = body.isEmpty() || body.endsWith("\n")
            val rawLines = if (body.isEmpty()) emptyList() else body.split("\r\n", "\n").let { if (body.endsWith("\n")) it.dropLast(1) else it }
            return IniDocument(rawLines.mapTo(mutableListOf()) { parseLine(it, dialect) }, separator, endsWithNewline, bom, dialect)
        }

        private fun isComment(trimmed: String, dialect: IniDialect) =
            trimmed.startsWith("#") || (dialect == IniDialect.PROPERTY_TREE && trimmed.startsWith(";"))

        private fun parseLine(raw: String, dialect: IniDialect): Line {
            if (isComment(raw.trim(), dialect)) return Line.Other(raw)
            val hash = if (dialect == IniDialect.PROGRAM_OPTIONS) raw.indexOf('#') else -1
            val content = if (hash >= 0) raw.substring(0, hash) else raw
            val trimmed = content.trim()
            if (trimmed.isEmpty()) return Line.Other(raw)
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                return Line.Section(raw, trimmed.substring(1, trimmed.length - 1).trim())
            }
            val eq = content.indexOf('=')
            if (eq <= 0) return Line.Other(raw)
            val key = content.substring(0, eq).trim()
            if (key.isEmpty() || key.any { it.isWhitespace() }) return Line.Other(raw)
            val indent = content.substring(0, content.length - content.trimStart().length)
            val value = content.substring(eq + 1).trim()
            // Comment with the spaces before it, so a rewritten line keeps its alignment
            val comment = if (hash >= 0) raw.substring(content.trimEnd().length) else ""
            return Line.Entry(raw, key, value, indent, comment)
        }
    }
}
