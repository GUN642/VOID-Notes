package app.voidnotes.data

import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/** Result of a Notion import. */
data class NotionResult(val pages: List<Page>, val attachments: Int, val rootIds: List<String>)

/**
 * Reads a Notion workspace export ("Markdown & CSV" ZIP): pages with their sub pages, images and files,
 * databases (CSV) with their entries. Pure file work – the caller puts the pages into the store.
 */
class NotionImport(private val store: Store, private val workDir: File, private val progress: (String) -> Unit) {
    private val idSuffix = Regex("\\s+[0-9a-f]{32}$")
    private val idPage = HashMap<String, String>() // canonical md path -> page id
    private val pages = ArrayList<Page>()
    private var attachments = 0
    private var order = 1L

    fun run(input: InputStream): NotionResult {
        workDir.deleteRecursively()
        workDir.mkdirs()
        progress("Entpacke …")
        unzip(input, workDir)
        // Single wrapper folder (e.g. "Export-…") is skipped.
        var root = workDir
        while (true) {
            val kids = root.listFiles().orEmpty().filterNot { it.name.startsWith(".") || it.name == "__MACOSX" }
            if (kids.size == 1 && kids[0].isDirectory) root = kids[0] else break
        }
        // First pass: an id for every Markdown file, so links between pages can be resolved.
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".md", true) }.forEach { idPage[it.canonicalPath] = newId() }
        // Databases: "X.csv" and "X_all.csv" are the same database.
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".csv", true) }.forEach { f ->
            val key = csvKey(f)
            // A full-page database comes as "X.md" plus "X_all.csv": both are the same page.
            val md = File(f.parentFile, f.name.replace(Regex("(_all)?\\.csv$", RegexOption.IGNORE_CASE), ".md"))
            val id = idPage[md.canonicalPath] ?: idPage.getOrPut(key) { newId() }
            idPage[key] = id
            idPage[f.canonicalPath] = id
        }
        if (idPage.isEmpty() && root.walkTopDown().none { it.name.endsWith(".csv", true) }) {
            error("Keine Notion-Seiten gefunden – bitte den Export im Format „Markdown & CSV“ wählen")
        }
        val roots = importDir(root, null)
        return NotionResult(pages, attachments, roots)
    }

    // ------------------------------------------------------------------ files & folders

    private fun unzip(input: InputStream, target: File) {
        val base = target.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val out = File(target, e.name)
                if (!out.canonicalPath.startsWith(base)) continue // zip slip
                if (e.isDirectory) {
                    out.mkdirs(); continue
                }
                out.parentFile?.mkdirs()
                if (e.name.endsWith(".zip", true)) {
                    // Large exports come as a ZIP of ZIPs.
                    val nested = File(out.parentFile, out.name.removeSuffix(".zip").removeSuffix(".ZIP"))
                    nested.mkdirs()
                    unzip(NonClosing(zip), nested)
                } else {
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private class NonClosing(private val inner: InputStream) : InputStream() {
        override fun read() = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, len)
        override fun close() {}
    }

    private fun clean(name: String) = name.replace(idSuffix, "").trim()

    private fun csvKey(f: File) = "csv:" + File(f.parentFile, f.name.replace(Regex("_all\\.csv$", RegexOption.IGNORE_CASE), ".csv")).canonicalPath

    private val consumed = HashSet<String>()

    /**
     * Folder that belongs to a page or database file: "Name 1a2b…cdef.md" → "Name 1a2b…cdef", "Name 1a2b-cdef"
     * (newer exports, when names repeat) or "Name". Every folder is used only once.
     */
    private fun folderFor(file: File): File? {
        val base = file.name.substringBeforeLast('.').replace(Regex("_all$", RegexOption.IGNORE_CASE), "")
        val dir = file.parentFile ?: return null
        val id = Regex("([0-9a-f]{32})$").find(base)?.value
        val candidates = listOfNotNull(
            File(dir, base),
            id?.let { File(dir, clean(base) + " " + it.take(4) + "-" + it.takeLast(4)) },
            File(dir, clean(base)),
        )
        val f = candidates.firstOrNull { it.isDirectory && it.canonicalPath !in consumed } ?: return null
        consumed += f.canonicalPath
        return f
    }

    /** Imports all pages, databases and plain folders in [dir]; returns the ids of the pages created there. */
    private fun importDir(dir: File, parentId: String?): List<String> {
        val entries = dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }
        val used = HashSet<String>()
        val created = ArrayList<String>()
        entries.filter { it.isFile && it.name.endsWith(".md", true) }.forEach { md ->
            val folder = folderFor(md)
            folder?.let { used += it.canonicalPath }
            created += importPage(md, folder, parentId)
        }
        // Databases: "X_all.csv" is the complete version of "X.csv".
        val csvs = entries.filter { it.isFile && it.name.endsWith(".csv", true) }
        val names = csvs.map { it.name }.toSet()
        val mdIds = entries.filter { it.isFile && it.name.endsWith(".md", true) }.mapNotNull { idPage[it.canonicalPath] }.toSet()
        csvs.filterNot { c -> !c.name.endsWith("_all.csv", true) && c.name.removeSuffix(".csv") + "_all.csv" in names }
            .filterNot { c -> idPage[c.canonicalPath] in mdIds }
            .forEach { csv ->
            val plain = File(csv.parentFile, csv.name.replace(Regex("_all\\.csv$", RegexOption.IGNORE_CASE), ".csv"))
            val folder = folderFor(plain)
            folder?.let { used += it.canonicalPath }
            created += importDatabase(csv, folder, parentId)
        }
        // Remaining folders: containers like "Private & Shared" – their pages belong to the same parent.
        entries.filter { it.isDirectory && it.canonicalPath !in used && it.canonicalPath !in consumed }.forEach { d ->
            consumed += d.canonicalPath
            if (d.walkTopDown().any { it.isFile && (it.name.endsWith(".md", true) || it.name.endsWith(".csv", true)) }) {
                created += importDir(d, parentId)
            }
        }
        return created
    }

    private fun importPage(md: File, folder: File?, parentId: String?): String {
        val id = idPage[md.canonicalPath] ?: newId()
        progress("Importiere ${pages.size + 1}: ${clean(md.name.removeSuffix(".md"))}")
        val text = runCatching { md.readText() }.getOrDefault("").removePrefix("﻿")
        val (title, blocks) = convert(text, md.parentFile!!)
        val children = folder?.let { importDir(it, id) }.orEmpty()
        // Sub pages that the page text does not link to still get a link at the end.
        val linked = blocks.mapNotNull { it.pageId }.toSet()
        val extra = children.filterNot { it in linked }.map { Block(type = BlockType.PAGE, pageId = it) }
        val (icon, cleanTitle) = splitIcon(title.ifBlank { clean(md.name.removeSuffix(".md")) })
        pages += Page(
            id = id, parentId = parentId, title = cleanTitle, icon = icon,
            blocks = (blocks + extra).ifEmpty { listOf(Block()) },
            order = order++,
        )
        return id
    }

    /** A leading emoji in the title becomes the page icon. */
    private fun splitIcon(title: String): Pair<String, String> {
        val t = title.trim()
        if (t.isEmpty()) return "" to t
        val cp = t.codePointAt(0)
        val emoji = Character.getType(cp) == Character.OTHER_SYMBOL.toInt() || cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF
        if (!emoji) return "" to t
        val end = t.indexOf(' ').takeIf { it > 0 } ?: return "" to t
        return t.substring(0, end) to t.substring(end + 1).trim()
    }

    private fun importDatabase(csv: File, folder: File?, parentId: String?): String {
        val id = idPage[csv.canonicalPath] ?: newId()
        val title = clean(csv.name.removeSuffix(".csv").removeSuffix("_all"))
        progress("Importiere Datenbank: $title")
        val rows = parseCsv(runCatching { csv.readText() }.getOrDefault("").removePrefix("﻿"))
        val header = rows.firstOrNull().orEmpty()
        val children = folder?.let { importDir(it, id) }.orEmpty()
        val childTitles = pages.filter { it.id in children }.associateBy { it.title.trim().lowercase() }
        val blocks = ArrayList<Block>()
        blocks += Block(type = BlockType.CALLOUT, emoji = "🗃️", text = "Aus einer Notion-Datenbank importiert (${(rows.size - 1).coerceAtLeast(0)} Einträge).")
        val linked = HashSet<String>()
        rows.drop(1).forEach { row ->
            val name = row.firstOrNull().orEmpty().trim()
            val page = childTitles[name.lowercase()]
            if (page != null && page.id !in linked) {
                linked += page.id
                blocks += Block(type = BlockType.PAGE, pageId = page.id)
            } else {
                val props = header.drop(1).zip(row.drop(1)).filter { it.second.isNotBlank() }
                    .joinToString(" · ") { (k, v) -> "$k: ${v.replace('\n', ' ')}" }
                blocks += Block(type = BlockType.BULLET, text = "**${name.ifBlank { "Ohne Titel" }}**" + if (props.isNotEmpty()) " – $props" else "")
            }
        }
        children.filterNot { it in linked }.forEach { blocks += Block(type = BlockType.PAGE, pageId = it) }
        pages += Page(id = id, parentId = parentId, title = title, icon = "🗃️", blocks = blocks, order = order++)
        return id
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = ArrayList() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    // ------------------------------------------------------------------ Markdown of one page

    private val linkRe = Regex("\\[([^\\]]*)]\\(([^)]+)\\)")
    private val imageLine = Regex("^!\\[([^\\]]*)]\\(([^)]+)\\)$")
    private val linkLine = Regex("^\\[([^\\]]*)]\\(([^)]+)\\)$")

    private fun resolve(dir: File, target: String): File? {
        if (target.startsWith("http://") || target.startsWith("https://")) return null
        val path = runCatching { URLDecoder.decode(target.substringBefore('#').replace("+", "%2B"), "UTF-8") }.getOrDefault(target)
        val f = File(dir, path)
        return f.takeIf { it.exists() }
    }

    /** Newer exports keep attachments without file extension – guess it from the first bytes. */
    private fun displayName(f: File): String {
        if (f.name.contains('.')) return f.name
        val head = ByteArray(16)
        val n = runCatching { f.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (n < 4) return f.name
        val s = String(head, 0, n, Charsets.ISO_8859_1)
        val ext = when {
            head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "jpg"
            s.startsWith("\u0089PNG") -> "png"
            s.startsWith("GIF8") -> "gif"
            s.startsWith("%PDF") -> "pdf"
            s.startsWith("RIFF") && s.substring(8).startsWith("WEBP") -> "webp"
            s.length >= 12 && s.substring(4, 8) == "ftyp" && s.substring(8, 12).startsWith("hei") -> "heic"
            s.length >= 12 && s.substring(4, 8) == "ftyp" -> "mp4"
            s.startsWith("PK") -> "zip"
            else -> return f.name
        }
        return "${f.name}.$ext"
    }

    private fun attach(f: File, image: Boolean): Block? = runCatching {
        val s = store.importLocalFile(f, displayName(f))
        attachments++
        Block(type = if (image || s.isImage) BlockType.IMAGE else BlockType.FILE, file = s.file, fileName = s.name, mime = s.mime, size = s.size)
    }.getOrNull()

    /** Inline links: to other pages → their title, to the web → "text (url)". */
    private fun inline(text: String, dir: File): String = linkRe.replace(text) { m ->
        val label = m.groupValues[1]
        val target = m.groupValues[2]
        when {
            target.startsWith("http") -> if (label == target || label.isBlank()) target else "$label ($target)"
            else -> label
        }
    }.replace("<br>", " ").replace("&nbsp;", " ").replace('\u00A0', ' ').trim()

    private fun propertyFiles(line: String, dir: File): Pair<String, List<File>>? {
        val m = Regex("^([^:]{1,40}): (.+)$").find(line) ?: return null
        val files = m.groupValues[2].split(", ").map { it.trim() }
        if (files.isEmpty() || files.any { !it.contains('/') }) return null
        val resolved = files.map { resolve(dir, it) ?: return null }
        return if (resolved.all { it.isFile }) m.groupValues[1] to resolved else null
    }

    private fun convert(markdown: String, dir: File): Pair<String, List<Block>> {
        val lines = markdown.replace("\r\n", "\n").lines()
        val out = ArrayList<Block>()
        var title = ""
        // Indentation unit: Notion uses 4 spaces, some tools 2 or tabs.
        val indents = lines.map { l -> l.length - l.trimStart().length }.filter { it > 0 }
        val unit = when {
            indents.isEmpty() -> 4
            indents.min() in 1..3 -> indents.min()
            else -> 4
        }
        var extraLevel = 0 // inside <details> toggles
        var i = 0
        while (i < lines.size) {
            val raw = lines[i].replace("\t", " ".repeat(unit))
            val t = raw.trim()
            val level = ((raw.length - raw.trimStart().length) / unit).coerceAtMost(6) + extraLevel
            when {
                t.isEmpty() -> {}
                title.isEmpty() && out.isEmpty() && t.startsWith("# ") -> title = inline(t.removePrefix("# "), dir).trim()
                t.startsWith("```") -> {
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i]); i++
                    }
                    out += Block(type = BlockType.CODE, text = code.toString(), level = extraLevel)
                }
                t.startsWith("<aside>") -> {
                    val body = StringBuilder(t.removePrefix("<aside>").removeSuffix("</aside>"))
                    if (!t.endsWith("</aside>")) {
                        i++
                        while (i < lines.size && !lines[i].trim().startsWith("</aside>")) {
                            val l = lines[i].trim()
                            if (l.isNotEmpty()) body.append(if (body.isNotBlank()) "\n" else "").append(l)
                            i++
                        }
                    }
                    var text = inline(body.toString().trim(), dir)
                    var emoji = "💡"
                    val first = text.substringBefore(' ')
                    if (first.isNotEmpty() && first.length <= 8 && splitIcon("$first x").first.isNotEmpty()) {
                        emoji = first; text = text.substringAfter(' ').trim()
                    }
                    out += Block(type = BlockType.CALLOUT, emoji = emoji, text = text, level = extraLevel)
                }
                t.startsWith("<details") -> {
                    val summary = Regex("<summary>(.*?)</summary>").find(t)?.groupValues?.get(1)
                        ?: lines.getOrNull(i + 1)?.let { Regex("<summary>(.*?)</summary>").find(it)?.groupValues?.get(1)?.also { i++ } }
                        ?: ""
                    out += Block(type = BlockType.TOGGLE, text = inline(summary.replace(Regex("<[^>]+>"), ""), dir), level = level)
                    extraLevel++
                }
                t.startsWith("</details>") -> extraLevel = (extraLevel - 1).coerceAtLeast(0)
                t.startsWith("<summary>") -> {}
                t.startsWith("|") -> {
                    // Tables stay readable as monospace text.
                    val table = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val row = lines[i].trim()
                        if (!Regex("^\\|[\\s:|-]+\\|$").matches(row)) {
                            if (table.isNotEmpty()) table.append('\n')
                            table.append(inline(row, dir))
                        }
                        i++
                    }
                    i--
                    out += Block(type = BlockType.CODE, text = table.toString(), level = extraLevel)
                }
                imageLine.matches(t) -> {
                    val m = imageLine.find(t)!!
                    val f = resolve(dir, m.groupValues[2])
                    out += (f?.let { attach(it, image = true) } ?: Block(text = m.groupValues[2]))
                        .copy(level = extraLevel)
                }
                linkLine.matches(t) -> {
                    val m = linkLine.find(t)!!
                    val target = m.groupValues[2]
                    val f = resolve(dir, target)
                    out += when {
                        f != null && f.name.endsWith(".md", true) && idPage[f.canonicalPath] != null ->
                            Block(type = BlockType.PAGE, pageId = idPage[f.canonicalPath], level = extraLevel)
                        f != null && f.name.endsWith(".csv", true) && idPage[f.canonicalPath] != null ->
                            Block(type = BlockType.PAGE, pageId = idPage[f.canonicalPath], level = extraLevel)
                        f != null && f.isFile -> attach(f, image = false)?.copy(level = extraLevel) ?: Block(text = m.groupValues[1])
                        else -> Block(text = inline(t, dir), level = level)
                    }
                }
                t.startsWith("#### ") || t.startsWith("### ") -> out += Block(type = BlockType.H3, text = inline(t.substringAfter(' '), dir), level = extraLevel)
                t.startsWith("## ") -> out += Block(type = BlockType.H2, text = inline(t.substring(3), dir), level = extraLevel)
                t.startsWith("# ") -> out += Block(type = BlockType.H1, text = inline(t.substring(2), dir), level = extraLevel)
                t == "---" || t == "***" -> out += Block(type = BlockType.DIVIDER, level = extraLevel)
                Regex("^[-*+] \\[( |x|X)] ").containsMatchIn(t) ->
                    out += Block(type = BlockType.TODO, text = inline(t.substring(6), dir), checked = t[3] != ' ', level = level)
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") ->
                    out += Block(type = BlockType.BULLET, text = inline(t.substring(2), dir), level = level)
                Regex("^\\d+[.)] ").containsMatchIn(t) ->
                    out += Block(type = BlockType.NUMBERED, text = inline(t.substringAfter(' '), dir), level = level)
                t.startsWith(">") -> out += Block(type = BlockType.QUOTE, text = inline(t.removePrefix(">").trim(), dir), level = extraLevel)
                propertyFiles(t, dir) != null -> {
                    // Database property with files, e.g. "Rechnung: ../attachments/…/Rechnung.pdf"
                    val (key, files) = propertyFiles(t, dir)!!
                    out += Block(text = "**$key:**", level = level)
                    files.forEach { f -> attach(f, image = false)?.let { out += it.copy(level = level) } }
                }
                else -> {
                    val text = inline(t.replace(Regex("</?(div|span|p)[^>]*>"), ""), dir)
                    val prev = out.lastOrNull()
                    // Lines directly below each other (no blank line) belong to one paragraph.
                    if (prev != null && prev.type == BlockType.TEXT && i > 0 && lines[i - 1].isNotBlank() && prev.level == level &&
                        !lines[i - 1].trim().let { it.startsWith("#") || it.startsWith("-") || it.startsWith("<") || it.startsWith("!") }
                    ) {
                        out[out.lastIndex] = prev.copy(text = prev.text + "\n" + text)
                    } else {
                        out += Block(text = text, level = level)
                    }
                }
            }
            i++
        }
        // Levels may only grow one step at a time.
        val fixed = ArrayList<Block>(out.size)
        out.forEach { b ->
            val max = (fixed.lastOrNull()?.level ?: -1) + 1
            fixed += if (b.level > max) b.copy(level = max.coerceAtLeast(0)) else b
        }
        return title to fixed
    }
}
