package app.voidnotes.data

enum class InlineStyle { BOLD, ITALIC, STRIKE, CODE, MARK, LINK }

/** A formatted range in a block's text, with the marker characters around it. */
data class InlineRange(val style: InlineStyle, val start: Int, val end: Int, val markers: List<IntRange>)

/** Text with markers removed, plus where each style applies in it. */
data class StyledText(val text: String, val ranges: List<Triple<InlineStyle, Int, Int>>)

/** Light inline Markdown: **fett**, *kursiv*, ~~durch~~, `code`, ==markiert== and links. */
object Inline {
    private val patterns = listOf(
        InlineStyle.CODE to Regex("`([^`\\n]+)`"),
        InlineStyle.BOLD to Regex("\\*\\*(.+?)\\*\\*"),
        InlineStyle.ITALIC to Regex("(?<![*\\w])\\*(?![*\\s])(.+?)(?<![*\\s])\\*(?![*\\w])"),
        InlineStyle.ITALIC to Regex("(?<![_\\w])_(?![_\\s])(.+?)(?<![_\\s])_(?![_\\w])"),
        InlineStyle.STRIKE to Regex("~~(.+?)~~"),
        InlineStyle.MARK to Regex("==(.+?)=="),
    )
    val url = Regex("(https?://|www\\.)[^\\s<>\"]+[^\\s<>\".,;:!?)\\]]")

    fun parse(text: String): List<InlineRange> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<InlineRange>()
        val codeSpans = ArrayList<IntRange>()
        for ((style, re) in patterns) {
            for (m in re.findAll(text)) {
                val whole = m.range
                // Nothing is formatted inside code.
                if (style != InlineStyle.CODE && codeSpans.any { it.first <= whole.first && whole.last <= it.last }) continue
                val inner = m.groups[1]!!.range
                out += InlineRange(
                    style, inner.first, inner.last + 1,
                    listOf(whole.first until inner.first, (inner.last + 1)..whole.last),
                )
                if (style == InlineStyle.CODE) codeSpans += whole
            }
        }
        for (m in url.findAll(text)) {
            if (codeSpans.any { it.first <= m.range.first && m.range.last <= it.last }) continue
            out += InlineRange(InlineStyle.LINK, m.range.first, m.range.last + 1, emptyList())
        }
        return out
    }

    /** Removes the markers (for PDF and plain text). */
    fun strip(text: String): StyledText {
        val ranges = parse(text)
        if (ranges.isEmpty()) return StyledText(text, emptyList())
        val hidden = BooleanArray(text.length)
        ranges.forEach { r -> r.markers.forEach { m -> for (i in m) if (i in hidden.indices) hidden[i] = true } }
        val map = IntArray(text.length + 1)
        val sb = StringBuilder()
        for (i in text.indices) {
            map[i] = sb.length
            if (!hidden[i]) sb.append(text[i])
        }
        map[text.length] = sb.length
        return StyledText(sb.toString(), ranges.map { Triple(it.style, map[it.start], map[it.end]) }.filter { it.second < it.third })
    }

    fun plain(text: String): String = strip(text).text

    fun urlAt(text: String, offset: Int): String? = url.findAll(text)
        .firstOrNull { offset in it.range.first..(it.range.last + 1) }?.value
        ?.let { if (it.startsWith("www.")) "https://$it" else it }
}

object MarkdownIO {
    /** The page as Markdown – the inline formatting already is Markdown. */
    fun export(page: Page, pages: Map<String, Page>): String = buildString {
        if (page.icon.isNotBlank()) append(page.icon).append(' ')
        append("# ").append(page.displayTitle).append("\n\n")
        val numbers = numbering(page.blocks)
        page.blocks.forEachIndexed { i, b ->
            val indent = "  ".repeat(b.level)
            when (b.type) {
                BlockType.TEXT -> append(indent).append(b.text).append("\n\n")
                BlockType.H1 -> append("## ").append(b.text).append("\n\n")
                BlockType.H2 -> append("### ").append(b.text).append("\n\n")
                BlockType.H3 -> append("#### ").append(b.text).append("\n\n")
                BlockType.BULLET -> append(indent).append("- ").append(b.text).append('\n')
                BlockType.NUMBERED -> append(indent).append(numbers[i]).append(". ").append(b.text).append('\n')
                BlockType.TODO -> append(indent).append(if (b.checked) "- [x] " else "- [ ] ").append(b.text).append('\n')
                BlockType.TOGGLE -> append(indent).append("- ▸ ").append(b.text).append('\n')
                BlockType.QUOTE -> append(b.text.lines().joinToString("\n") { "> $it" }).append("\n\n")
                BlockType.CALLOUT -> append("> ").append(b.emoji ?: "💡").append(' ').append(b.text).append("\n\n")
                BlockType.CODE -> append("```\n").append(b.text).append("\n```\n\n")
                BlockType.DIVIDER -> append("---\n\n")
                BlockType.IMAGE -> append("![").append(b.fileName ?: "Bild").append("](").append(b.fileName ?: "").append(")\n\n")
                BlockType.FILE -> append("📎 ").append(b.fileName ?: "Datei").append("\n\n")
                BlockType.PAGE -> append("↳ ").append(pages[b.pageId]?.let { (it.icon + " " + it.displayTitle).trim() } ?: "Seite").append("\n\n")
            }
        }
    }.replace(Regex("\n{3,}"), "\n\n").trim() + "\n"

    fun plainText(page: Page, pages: Map<String, Page>): String = buildString {
        append(page.displayTitle).append("\n\n")
        val numbers = numbering(page.blocks)
        page.blocks.forEachIndexed { i, b ->
            val indent = "   ".repeat(b.level)
            val text = Inline.plain(b.text)
            when (b.type) {
                BlockType.BULLET -> append(indent).append("• ").append(text)
                BlockType.NUMBERED -> append(indent).append(numbers[i]).append(". ").append(text)
                BlockType.TODO -> append(indent).append(if (b.checked) "☑ " else "☐ ").append(text)
                BlockType.TOGGLE -> append(indent).append("▸ ").append(text)
                BlockType.DIVIDER -> append("———")
                BlockType.IMAGE, BlockType.FILE -> append("📎 ").append(b.fileName ?: "Datei")
                BlockType.PAGE -> append("↳ ").append(pages[b.pageId]?.displayTitle ?: "Seite")
                BlockType.CALLOUT -> append(b.emoji ?: "💡").append(' ').append(text)
                else -> append(indent).append(text)
            }
            append('\n')
        }
    }.trim()

    /** Imports Markdown (or plain text) into blocks. Returns the title (first heading) and the blocks. */
    fun import(markdown: String, extractTitle: Boolean = true): Pair<String, List<Block>> {
        val blocks = ArrayList<Block>()
        var title = ""
        val lines = markdown.replace("\r\n", "\n").lines()
        var i = 0
        val para = StringBuilder()
        fun flush() {
            if (para.isNotBlank()) blocks += Block(type = BlockType.TEXT, text = para.toString().trim())
            para.clear()
        }
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            val spaces = raw.length - raw.trimStart().length
            val level = (spaces / 2).coerceAtMost(6)
            val t = line.trimStart()
            when {
                t.startsWith("```") -> {
                    flush()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i]); i++
                    }
                    blocks += Block(type = BlockType.CODE, text = code.toString())
                }
                t.isEmpty() -> flush()
                t.startsWith("# ") && extractTitle && title.isEmpty() && blocks.isEmpty() -> { flush(); title = t.removePrefix("# ").trim() }
                t.startsWith("#### ") -> { flush(); blocks += Block(type = BlockType.H3, text = t.substring(5)) }
                t.startsWith("### ") -> { flush(); blocks += Block(type = BlockType.H3, text = t.substring(4)) }
                t.startsWith("## ") -> { flush(); blocks += Block(type = BlockType.H2, text = t.substring(3)) }
                t.startsWith("# ") -> { flush(); blocks += Block(type = BlockType.H1, text = t.substring(2)) }
                t == "---" || t == "***" || t == "___" -> { flush(); blocks += Block(type = BlockType.DIVIDER) }
                Regex("^[-*+] \\[( |x|X)] ").containsMatchIn(t) -> {
                    flush(); blocks += Block(type = BlockType.TODO, text = t.substring(6), checked = t[3] != ' ', level = level)
                }
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") || t.startsWith("• ") -> {
                    flush(); blocks += Block(type = BlockType.BULLET, text = t.substring(2), level = level)
                }
                Regex("^\\d+[.)] ").containsMatchIn(t) -> {
                    flush(); blocks += Block(type = BlockType.NUMBERED, text = t.substringAfter(' '), level = level)
                }
                t.startsWith("> ") || t == ">" -> {
                    flush(); blocks += Block(type = BlockType.QUOTE, text = t.removePrefix(">").trim())
                }
                else -> {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(t)
                }
            }
            i++
        }
        flush()
        return title to blocks
    }
}

/** Number shown in front of each numbered list item (0 for other blocks). */
fun numbering(blocks: List<Block>): IntArray {
    val out = IntArray(blocks.size)
    val counters = IntArray(16)
    blocks.forEachIndexed { i, b ->
        val lvl = b.level.coerceIn(0, 15)
        if (b.type == BlockType.NUMBERED) {
            counters[lvl]++
            out[i] = counters[lvl]
            for (d in lvl + 1 until 16) counters[d] = 0
        } else {
            // Any other block on the same or a lower level ends the list there.
            for (d in lvl until 16) counters[d] = 0
        }
    }
    return out
}
