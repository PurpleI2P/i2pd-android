package org.purplei2p.i2pd.config

/**
 * INI file in the boost::program_options flavour used by i2pd, edited without losing anything:
 * comments, blank lines, order, formatting and unknown lines are written back as they were read.
 *
 * Only the changed lines are rewritten. Keys before the first section belong to the global section "".
 * In i2pd files "#" starts a comment anywhere in a line.
 */
class IniDocument private constructor(
    private val lines: MutableList<Line>,
    private val lineSeparator: String,
    private val endsWithNewline: Boolean,
    private val bom: Boolean,
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
        fun parse(text: String): IniDocument {
            val bom = text.startsWith('﻿')
            val body = if (bom) text.substring(1) else text
            val separator = if (body.contains("\r\n")) "\r\n" else "\n"
            val endsWithNewline = body.endsWith("\n")
            val rawLines = body.split("\r\n", "\n").let { if (endsWithNewline) it.dropLast(1) else it }
            return IniDocument(rawLines.mapTo(mutableListOf(), ::parseLine), separator, endsWithNewline, bom)
        }

        private fun parseLine(raw: String): Line {
            val hash = raw.indexOf('#')
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
