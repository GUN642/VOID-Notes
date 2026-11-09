package app.voidnotes.ui.editor

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.voidnotes.data.Block
import app.voidnotes.data.BlockType
import app.voidnotes.data.MarkdownIO
import app.voidnotes.data.newId

/** Invisible first character of every text field: deleting it means "backspace at the start". */
const val ZW = '​'

/** Ask a block (or the title, id [TITLE]) to take the focus; offset -1 = end of the text. */
data class FocusRequest(val id: String, val offset: Int, val nonce: Long = System.nanoTime())

const val TITLE = "#title"

sealed interface EditTarget {
    val id: String

    data class PageTarget(override val id: String) : EditTarget
    data class TemplateTarget(override val id: String) : EditTarget
}

class EditBlock(b: Block) {
    val id: String = b.id
    var type by mutableStateOf(b.type)
    var value by mutableStateOf(TextFieldValue(b.text, TextRange(b.text.length)))
    var checked by mutableStateOf(b.checked)
    var level by mutableIntStateOf(b.level)
    var collapsed by mutableStateOf(b.collapsed)
    var file by mutableStateOf(b.file)
    var fileName by mutableStateOf(b.fileName)
    var mime by mutableStateOf(b.mime)
    var size by mutableLongStateOf(b.size)
    var pageId by mutableStateOf(b.pageId)
    var emoji by mutableStateOf(b.emoji)

    val text: String get() = value.text

    fun toBlock() = Block(
        id = id, type = type, text = value.text, checked = checked, level = level, collapsed = collapsed,
        file = file, fileName = fileName, mime = mime, size = size, pageId = pageId, emoji = emoji,
    )
}

/** Editing state of one page or template: a list of blocks with Notion-like behaviour. */
class EditorState(
    val target: EditTarget,
    title: String,
    icon: String,
    blocks: List<Block>,
    private val onChange: () -> Unit,
) {
    var title by mutableStateOf(TextFieldValue(title, TextRange(title.length)))
    var icon by mutableStateOf(icon)
    val blocks = mutableStateListOf<EditBlock>().apply { addAll(blocks.map(::EditBlock)) }

    var focusRequest by mutableStateOf<FocusRequest?>(null)
    /** Block with the keyboard focus (or [TITLE]). */
    var focusedId by mutableStateOf<String?>(null)
    /** Last block that had the focus – where attachments go when the keyboard is closed. */
    var lastFocusedId: String? = null
    /** Block showing the "/" menu. */
    var slashFor by mutableStateOf<String?>(null)
    var slashIndex by mutableIntStateOf(0)

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    fun index(id: String) = blocks.indexOfFirst { it.id == id }
    fun block(id: String?) = id?.let { i -> blocks.firstOrNull { it.id == i } }
    val focused: EditBlock? get() = block(focusedId)

    fun snapshotBlocks(): List<Block> = blocks.map { it.toBlock() }

    fun changed() = onChange()

    fun requestFocus(id: String, offset: Int = -1) {
        focusRequest = FocusRequest(id, offset)
    }

    // ------------------------------------------------------------------ undo

    private data class Snap(val title: String, val icon: String, val blocks: List<Block>, val focus: String?)

    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    private var lastKey: String? = null
    private var lastTime = 0L

    private fun snap() = Snap(title.text, icon, snapshotBlocks(), focusedId)

    /** Remembers the state before a change; typing in the same block is grouped into one step. */
    fun checkpoint(key: String? = null) {
        val now = SystemClock.uptimeMillis()
        if (key != null && key == lastKey && now - lastTime < 1500) {
            lastTime = now; return
        }
        lastKey = key; lastTime = now
        undoStack.addLast(snap())
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true; canRedo = false
    }

    fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snap())
        restore(s)
    }

    fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snap())
        restore(s)
    }

    private fun restore(s: Snap) {
        lastKey = null
        title = TextFieldValue(s.title, TextRange(s.title.length))
        icon = s.icon
        blocks.clear()
        blocks.addAll(s.blocks.map(::EditBlock))
        slashFor = null
        val f = s.focus?.takeIf { id -> id == TITLE || blocks.any { it.id == id && it.type.hasText } }
        if (f != null) requestFocus(f)
        canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty()
        changed()
    }

    // ------------------------------------------------------------------ structure helpers

    /** Index after the last block nested under the block at [i]. */
    fun endOfChildren(i: Int): Int {
        val lvl = blocks[i].level
        var j = i + 1
        while (j < blocks.size && blocks[j].level > lvl) j++
        return j
    }

    /** Blocks not hidden inside a closed toggle. */
    fun visibleBlocks(): List<EditBlock> {
        val out = ArrayList<EditBlock>(blocks.size)
        var hideAbove = Int.MAX_VALUE
        for (b in blocks) {
            if (b.level > hideAbove) continue
            hideAbove = Int.MAX_VALUE
            out += b
            if (b.type == BlockType.TOGGLE && b.collapsed) hideAbove = b.level
        }
        return out
    }

    private fun neighbourText(b: EditBlock, delta: Int): EditBlock? {
        val vis = visibleBlocks()
        var i = vis.indexOfFirst { it.id == b.id }
        if (i < 0) return null
        while (true) {
            i += delta
            val n = vis.getOrNull(i) ?: return null
            if (n.type.hasText) return n
        }
    }

    fun focusNeighbour(b: EditBlock, delta: Int): Boolean {
        val n = neighbourText(b, delta)
        if (n == null) {
            if (delta < 0) requestFocus(TITLE)
            return delta < 0
        }
        requestFocus(n.id, if (delta < 0) -1 else 0)
        return true
    }

    // ------------------------------------------------------------------ typing

    fun onTitle(v: TextFieldValue) {
        if (v.text.contains('\n')) {
            title = TextFieldValue(v.text.replace("\n", ""), TextRange(v.text.indexOf('\n')))
            val first = blocks.firstOrNull()
            if (first != null && first.type.hasText) requestFocus(first.id, 0)
            else {
                val nb = EditBlock(Block())
                blocks.add(0, nb)
                requestFocus(nb.id, 0)
            }
            changed(); return
        }
        if (v.text != title.text) checkpoint(TITLE)
        val textChanged = v.text != title.text
        title = v
        if (textChanged) changed()
    }

    fun onText(b: EditBlock, new: TextFieldValue) {
        val old = b.value
        if (new.text == old.text) {
            b.value = new
            updateSlash(b)
            return
        }
        checkpoint("text:" + b.id)
        val oldNl = old.text.count { it == '\n' }
        val newNl = new.text.count { it == '\n' }
        if (b.type != BlockType.CODE && newNl > oldNl) {
            val sel = new.selection.start
            if (newNl - oldNl == 1 && new.selection.collapsed && sel > 0 && new.text[sel - 1] == '\n') {
                enter(b, new.text.substring(0, sel - 1), new.text.substring(sel))
                return
            }
            paste(b, new.text)
            return
        }
        b.value = new
        if (applyShortcut(b)) {
            changed(); return
        }
        updateSlash(b)
        changed()
    }

    /** Several lines arrived at once (pasted): the first stays here, the rest become blocks (Markdown understood). */
    private fun paste(b: EditBlock, text: String) {
        val first = text.substringBefore('\n')
        val rest = text.substringAfter('\n')
        b.value = TextFieldValue(first, TextRange(first.length))
        val parsed = MarkdownIO.import(rest, extractTitle = false).second
            .map { if (b.type.continues && it.type == BlockType.TEXT) it.copy(type = b.type) else it }
            .map { it.copy(level = it.level + b.level) }
        var at = index(b.id) + 1
        var last = b
        parsed.forEach { pb ->
            val eb = EditBlock(pb)
            blocks.add(at++, eb)
            last = eb
        }
        if (!last.type.hasText) {
            val nb = EditBlock(Block(level = b.level))
            blocks.add(at, nb)
            last = nb
        }
        requestFocus(last.id, -1)
        changed()
    }

    private fun enter(b: EditBlock, before: String, after: String) {
        slashFor = null
        val listish = b.type in setOf(BlockType.BULLET, BlockType.NUMBERED, BlockType.TODO, BlockType.TOGGLE, BlockType.QUOTE, BlockType.CALLOUT)
        // Enter on an empty list item ends the list (or steps out one level).
        if (before.isEmpty() && after.isEmpty() && listish) {
            if (b.level > 0) shiftLevel(b, -1) else b.type = BlockType.TEXT
            b.value = TextFieldValue("")
            changed(); return
        }
        // Enter at the start of a filled block puts an empty block above it.
        if (before.isEmpty() && after.isNotEmpty()) {
            b.value = TextFieldValue(after, TextRange(0))
            val nb = EditBlock(Block(type = if (b.type.continues) b.type else BlockType.TEXT, level = b.level))
            blocks.add(index(b.id), nb)
            requestFocus(b.id, 0)
            changed(); return
        }
        b.value = TextFieldValue(before, TextRange(before.length))
        val i = index(b.id)
        val (type, level, at) = when {
            b.type.continues -> Triple(b.type, b.level, i + 1)
            b.type == BlockType.TOGGLE && !b.collapsed -> Triple(BlockType.TEXT, b.level + 1, i + 1)
            b.type == BlockType.TOGGLE -> Triple(BlockType.TOGGLE, b.level, endOfChildren(i))
            else -> Triple(BlockType.TEXT, b.level, i + 1)
        }
        val nb = EditBlock(Block(type = type, text = after, level = level))
        blocks.add(at, nb)
        requestFocus(nb.id, 0)
        changed()
    }

    /** Backspace at the very start of a block. */
    fun backspaceAtStart(b: EditBlock) {
        checkpoint()
        slashFor = null
        if (b.type != BlockType.TEXT && b.type.hasText) {
            b.type = BlockType.TEXT
            changed(); return
        }
        if (b.level > 0) {
            b.level -= 1
            changed(); return
        }
        val i = index(b.id)
        if (i <= 0) {
            if (b.text.isEmpty() && blocks.size > 1) {
                blocks.removeAt(i)
                requestFocus(TITLE)
                changed()
            } else requestFocus(TITLE)
            return
        }
        val above = visibleBlocks().let { v -> v.getOrNull(v.indexOfFirst { it.id == b.id } - 1) } ?: return
        if (!above.type.hasText) {
            if (b.text.isEmpty()) {
                blocks.removeAt(i)
                neighbourText(above, -1)?.let { requestFocus(it.id, -1) } ?: requestFocus(TITLE)
                changed()
            }
            return
        }
        val join = above.text.length
        above.value = TextFieldValue(above.text + b.text, TextRange(join))
        blocks.removeAt(i)
        requestFocus(above.id, join)
        changed()
    }

    /** Soft line break inside a block (Shift+Enter). */
    fun insertText(b: EditBlock, s: String) {
        checkpoint("text:" + b.id)
        val v = b.value
        val t = v.text.replaceRange(v.selection.min, v.selection.max, s)
        b.value = TextFieldValue(t, TextRange(v.selection.min + s.length))
        changed()
    }

    // ------------------------------------------------------------------ Markdown shortcuts & "/" menu

    private val prefixRules = listOf(
        "### " to BlockType.H3, "## " to BlockType.H2, "# " to BlockType.H1,
        "- " to BlockType.BULLET, "* " to BlockType.BULLET, "• " to BlockType.BULLET, "+ " to BlockType.BULLET,
        "[] " to BlockType.TODO, "[ ] " to BlockType.TODO,
        "> " to BlockType.TOGGLE,
        "\" " to BlockType.QUOTE, "„ " to BlockType.QUOTE, "“ " to BlockType.QUOTE, "| " to BlockType.QUOTE,
        "! " to BlockType.CALLOUT,
    )
    private val numberedRule = Regex("^\\d{1,3}[.)] ")

    private fun applyShortcut(b: EditBlock): Boolean {
        val v = b.value
        if (!v.selection.collapsed) return false
        val t = v.text
        val cur = v.selection.start
        if (b.type == BlockType.TEXT) {
            for ((p, type) in prefixRules) {
                if (cur == p.length && t.startsWith(p)) {
                    convert(b, type, t.substring(p.length)); return true
                }
            }
            if (cur == 4 && (t.startsWith("[x] ") || t.startsWith("[X] "))) {
                convert(b, BlockType.TODO, t.substring(4)); b.checked = true; return true
            }
            numberedRule.find(t)?.let { m ->
                if (cur == m.value.length) {
                    convert(b, BlockType.NUMBERED, t.substring(m.value.length)); return true
                }
            }
            if (t == "```") {
                convert(b, BlockType.CODE, ""); return true
            }
            if (t == "---" || t == "***" || t == "—-" || t == "-—" || t == "___") {
                b.value = TextFieldValue("")
                setType(b, BlockType.DIVIDER, record = false)
                return true
            }
        }
        if (b.type == BlockType.BULLET && cur == 3 && t.startsWith("[] ")) {
            convert(b, BlockType.TODO, t.substring(3)); return true
        }
        return false
    }

    private fun convert(b: EditBlock, type: BlockType, rest: String) {
        b.type = type
        b.value = TextFieldValue(rest, TextRange(0))
        if (type == BlockType.CALLOUT && b.emoji == null) b.emoji = "💡"
        slashFor = null
    }

    private fun updateSlash(b: EditBlock) {
        val t = b.text
        val cur = b.value.selection.start
        val active = b.type.hasText && b.type != BlockType.CODE && t.startsWith("/") && cur >= 1 && t.length <= 24 &&
            !t.contains('\n') && !t.contains("  ")
        if (active) {
            if (slashFor != b.id) slashIndex = 0
            slashFor = b.id
        } else if (slashFor == b.id) {
            slashFor = null
        }
    }

    fun slashQuery(b: EditBlock): String = b.text.drop(1).take((b.value.selection.start - 1).coerceAtLeast(0))

    /** Removes the "/query" text that opened the menu. */
    fun clearSlash(b: EditBlock) {
        val t = b.text
        val end = t.indexOf(' ').takeIf { it > 0 } ?: t.length
        b.value = TextFieldValue(t.substring(end).trimStart(), TextRange(0))
        slashFor = null
    }

    // ------------------------------------------------------------------ block operations

    fun setType(b: EditBlock, type: BlockType, record: Boolean = true) {
        if (record) checkpoint()
        if (type == BlockType.DIVIDER) {
            val keepText = b.text
            b.type = BlockType.DIVIDER
            b.value = TextFieldValue("")
            val i = index(b.id)
            val next = blocks.getOrNull(i + 1)
            val target = if (next != null && next.type == BlockType.TEXT && next.text.isEmpty()) next else {
                EditBlock(Block(text = keepText, level = b.level)).also { blocks.add(i + 1, it) }
            }
            requestFocus(target.id, 0)
            changed(); return
        }
        b.type = type
        if (type == BlockType.CALLOUT && b.emoji == null) b.emoji = "💡"
        if (type.hasText) requestFocus(b.id, b.value.selection.start)
        changed()
    }

    fun toggleChecked(b: EditBlock) {
        checkpoint()
        b.checked = !b.checked
        changed()
    }

    fun toggleCollapsed(b: EditBlock) {
        checkpoint()
        b.collapsed = !b.collapsed
        if (b.collapsed) {
            val i = index(b.id)
            val end = endOfChildren(i)
            if (blocks.subList(i + 1, end).any { it.id == focusedId }) requestFocus(b.id)
        } else if (endOfChildren(index(b.id)) == index(b.id) + 1) {
            // Opening an empty toggle gives it a first line to write in.
            val nb = EditBlock(Block(level = b.level + 1))
            blocks.add(index(b.id) + 1, nb)
            requestFocus(nb.id, 0)
        }
        changed()
    }

    /** Changes the level of a block together with everything nested under it. */
    private fun shiftLevel(b: EditBlock, delta: Int) {
        val i = index(b.id)
        val end = endOfChildren(i)
        for (j in i until end) blocks[j].level = (blocks[j].level + delta).coerceIn(0, 8)
    }

    fun indent(b: EditBlock) {
        val i = index(b.id)
        if (i <= 0) return
        if (b.level > blocks[i - 1].level || b.level >= 8) return
        checkpoint()
        shiftLevel(b, +1)
        requestFocus(b.id, b.value.selection.start)
        changed()
    }

    fun outdent(b: EditBlock) {
        if (b.level == 0) return
        checkpoint()
        shiftLevel(b, -1)
        requestFocus(b.id, b.value.selection.start)
        changed()
    }

    /** Moves the block with its nested blocks one step up or down. */
    fun move(b: EditBlock, delta: Int) {
        val i = index(b.id)
        if (i < 0) return
        val end = endOfChildren(i)
        if (delta < 0) {
            if (i == 0) return
            var p = i - 1
            while (p > 0 && blocks[p].level > b.level) p--
            checkpoint()
            val chunk = blocks.subList(i, end).toList()
            repeat(chunk.size) { blocks.removeAt(i) }
            blocks.addAll(p, chunk)
            fixLevels(p, chunk.size)
        } else {
            if (end >= blocks.size) return
            val nextEnd = endOfChildren(end)
            checkpoint()
            val next = blocks.subList(end, nextEnd).toList()
            repeat(next.size) { blocks.removeAt(end) }
            blocks.addAll(i, next)
            fixLevels(i + next.size, end - i)
        }
        if (b.type.hasText) requestFocus(b.id, b.value.selection.start)
        changed()
    }

    /** A block can be nested at most one level deeper than the block above it. */
    private fun fixLevels(from: Int, count: Int) {
        if (from >= blocks.size) return
        val top = blocks[from]
        val maxLevel = if (from == 0) 0 else blocks[from - 1].level + 1
        val delta = (minOf(top.level, maxLevel) - top.level)
        if (delta != 0) for (j in from until (from + count).coerceAtMost(blocks.size)) blocks[j].level = (blocks[j].level + delta).coerceAtLeast(0)
    }

    fun duplicate(b: EditBlock) {
        val i = index(b.id)
        if (i < 0) return
        checkpoint()
        val end = endOfChildren(i)
        val copies = blocks.subList(i, end).map { EditBlock(it.toBlock().copy(id = newId(), pageId = if (it.type == BlockType.PAGE) it.pageId else null)) }
        blocks.addAll(end, copies)
        if (copies.first().type.hasText) requestFocus(copies.first().id, -1)
        changed()
    }

    fun delete(b: EditBlock) {
        val i = index(b.id)
        if (i < 0) return
        checkpoint()
        val end = endOfChildren(i)
        // Nested blocks move up one level instead of disappearing.
        for (j in i + 1 until end) blocks[j].level = (blocks[j].level - 1).coerceAtLeast(0)
        blocks.removeAt(i)
        if (blocks.isEmpty()) blocks.add(EditBlock(Block()))
        val focus = blocks.getOrNull(i - 1)?.takeIf { it.type.hasText } ?: blocks.getOrNull(i)?.takeIf { it.type.hasText }
        focus?.let { requestFocus(it.id, -1) }
        if (slashFor == b.id) slashFor = null
        changed()
    }

    /**
     * Inserts blocks after the focused block (replacing it when it is an empty line) or at the end,
     * and makes sure there is a line to keep writing below them.
     */
    fun insertBlocks(newBlocks: List<Block>, afterId: String? = focusedId ?: lastFocusedId) {
        if (newBlocks.isEmpty()) return
        checkpoint()
        val anchor = block(afterId)
        var at: Int
        var level = 0
        if (anchor != null) {
            val i = index(anchor.id)
            level = anchor.level
            if (anchor.type == BlockType.TEXT && anchor.text.isBlank()) {
                blocks.removeAt(i); at = i
            } else at = endOfChildren(i)
        } else {
            val last = blocks.lastOrNull()
            if (last != null && last.type == BlockType.TEXT && last.text.isBlank() && last.level == 0) {
                blocks.removeAt(blocks.lastIndex)
            }
            at = blocks.size
        }
        val added = newBlocks.map { EditBlock(it.copy(level = level)) }
        blocks.addAll(at, added)
        at += added.size
        val after = blocks.getOrNull(at)
        val writeLine = if (after != null && after.type == BlockType.TEXT && after.text.isEmpty()) after else {
            EditBlock(Block(level = level)).also { blocks.add(at, it) }
        }
        val lastAdded = added.last()
        requestFocus(if (lastAdded.type.hasText) lastAdded.id else writeLine.id, -1)
        changed()
    }

    /** Puts [marker] around the selection (or toggles it off again). */
    fun wrap(b: EditBlock, marker: String) {
        checkpoint()
        val v = b.value
        val t = v.text
        val s = v.selection.min
        val e = v.selection.max
        val before = t.substring(0, s)
        val after = t.substring(e)
        if (before.endsWith(marker) && after.startsWith(marker)) {
            val nt = before.dropLast(marker.length) + t.substring(s, e) + after.drop(marker.length)
            b.value = TextFieldValue(nt, TextRange(s - marker.length, e - marker.length))
        } else {
            val nt = before + marker + t.substring(s, e) + marker + after
            b.value = TextFieldValue(nt, if (s == e) TextRange(s + marker.length) else TextRange(s + marker.length, e + marker.length))
        }
        requestFocus(b.id, b.value.selection.start)
        changed()
    }

    /** Adds an empty line at the end (tap below the last block). */
    fun focusEnd() {
        val last = blocks.lastOrNull()
        if (last != null && last.type == BlockType.TEXT && last.text.isEmpty() && last.level == 0) {
            requestFocus(last.id, 0); return
        }
        val nb = EditBlock(Block())
        blocks.add(nb)
        requestFocus(nb.id, 0)
        changed()
    }

    /**
     * Shows changes made by someone else (shared pages). Existing blocks keep their identity so the
     * cursor stays where it is; the block being typed in is never overwritten.
     */
    fun applyExternal(newTitle: String, newIcon: String, newBlocks: List<Block>) {
        if (focusedId != TITLE && newTitle != title.text) title = TextFieldValue(newTitle, TextRange(newTitle.length))
        if (newIcon != icon) icon = newIcon
        val existing = blocks.associateBy { it.id }
        val list = newBlocks.map { nb ->
            val eb = existing[nb.id]
            if (eb == null) EditBlock(nb) else {
                if (eb.id != focusedId) {
                    eb.type = nb.type
                    if (eb.text != nb.text) eb.value = TextFieldValue(nb.text, TextRange(nb.text.length))
                    eb.checked = nb.checked
                    eb.level = nb.level
                    eb.collapsed = nb.collapsed
                    eb.file = nb.file; eb.fileName = nb.fileName; eb.mime = nb.mime; eb.size = nb.size
                    eb.pageId = nb.pageId; eb.emoji = nb.emoji
                }
                eb
            }
        }.toMutableList()
        // Never lose the block being typed in, even if it was deleted elsewhere.
        val f = existing[focusedId]
        if (f != null && list.none { it.id == f.id }) list.add((blocks.indexOf(f)).coerceIn(0, list.size), f)
        if (list.isEmpty()) list.add(EditBlock(Block()))
        if (list.map { it.id } != blocks.map { it.id }) {
            blocks.clear()
            blocks.addAll(list)
        }
    }

    fun appendBlock(block: Block) {
        blocks.add(EditBlock(block))
        changed()
    }
}
