package app.voidnotes.ui.editor

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.voidnotes.data.BlockType
import app.voidnotes.data.Inline
import app.voidnotes.data.InlineStyle
import app.voidnotes.data.Page
import app.voidnotes.data.PdfWriter
import app.voidnotes.ui.MenuItem
import app.voidnotes.ui.PageIcon
import app.voidnotes.ui.VoidMenu
import app.voidnotes.ui.formatSize
import app.voidnotes.ui.onRightClick
import app.voidnotes.ui.theme.MonoFont
import app.voidnotes.ui.theme.VoidColors
import app.voidnotes.ui.theme.VoidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------ text field helpers

/** The stored value with the invisible start character in front. */
private fun TextFieldValue.withSentinel() = TextFieldValue(
    ZW + text,
    TextRange(selection.start + 1, selection.end + 1),
    composition?.let { TextRange(it.start + 1, it.end + 1) },
)

/** Removes every invisible start character and fixes the cursor positions. */
private fun TextFieldValue.withoutSentinel(): TextFieldValue {
    val raw = text
    fun map(pos: Int): Int {
        var n = 0
        for (i in 0 until pos.coerceAtMost(raw.length)) if (raw[i] == ZW) n++
        return (pos - n).coerceAtLeast(0)
    }
    val clean = raw.replace(ZW.toString(), "")
    return TextFieldValue(
        clean,
        TextRange(map(selection.start).coerceAtMost(clean.length), map(selection.end).coerceAtMost(clean.length)),
        composition?.let { TextRange(map(it.start).coerceAtMost(clean.length), map(it.end).coerceAtMost(clean.length)) },
    )
}

/** Shows inline Markdown live: the markers stay (dimmed), the content gets its style. */
private class InlineMarkdown(private val colors: VoidColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val offset = if (raw.startsWith(ZW)) 1 else 0
        val ranges = Inline.parse(raw.substring(offset))
        if (ranges.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val b = AnnotatedString.Builder(text)
        val markerStyle = SpanStyle(color = colors.textMuted.copy(alpha = 0.55f), fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal)
        ranges.forEach { r ->
            val style = when (r.style) {
                InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                InlineStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                InlineStyle.CODE -> SpanStyle(fontFamily = MonoFont, background = colors.surfaceHigh, fontSize = 0.9.em)
                InlineStyle.MARK -> SpanStyle(background = colors.accent.copy(alpha = 0.28f))
                InlineStyle.LINK -> SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)
            }
            b.addStyle(style, r.start + offset, r.end + offset)
            r.markers.forEach { m -> if (!m.isEmpty()) b.addStyle(markerStyle, m.first + offset, m.last + 1 + offset) }
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is InlineMarkdown && other.colors == colors
    override fun hashCode() = colors.hashCode()
}

fun blockStyle(type: BlockType, colors: VoidColors, scale: Float, checked: Boolean): TextStyle {
    val base = TextStyle(color = colors.text, fontSize = (16 * scale).sp, lineHeight = (24 * scale).sp)
    return when (type) {
        BlockType.H1 -> base.copy(fontSize = (28 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.Bold)
        BlockType.H2 -> base.copy(fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold)
        BlockType.H3 -> base.copy(fontSize = (18.5 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        BlockType.QUOTE -> base.copy(fontStyle = FontStyle.Italic, fontSize = (17 * scale).sp)
        BlockType.CODE -> base.copy(fontFamily = MonoFont, fontSize = (13.5 * scale).sp, lineHeight = (21 * scale).sp)
        BlockType.TODO -> if (checked) base.copy(color = colors.textMuted, textDecoration = TextDecoration.LineThrough) else base
        else -> base
    }
}

// ------------------------------------------------------------------ text block

/**
 * The text field of a block, with all keyboard behaviour (Enter splits, Backspace merges,
 * Markdown shortcuts, DeX key combinations).
 */
@Composable
fun BlockField(
    editor: EditorState,
    b: EditBlock,
    scale: Float,
    spellCheck: Boolean,
    onSlashPick: (EditBlock) -> Boolean,
    onShortcut: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val c = VoidTheme.colors
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    val request = editor.focusRequest
    LaunchedEffect(request) {
        if (request == null || request.id != b.id) return@LaunchedEffect
        val off = if (request.offset < 0) b.text.length else request.offset.coerceIn(0, b.text.length)
        b.value = b.value.copy(selection = TextRange(off), composition = null)
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        if (editor.focusRequest == request) editor.focusRequest = null
    }

    val style = blockStyle(b.type, c, scale, b.checked)
    val transformation = remember(c) { InlineMarkdown(c) }
    BasicTextField(
        value = b.value.withSentinel(),
        onValueChange = { nv ->
            if (editor.slashFor == b.id && nv.text.count { it == '\n' } > b.text.count { it == '\n' }) {
                onSlashPick(b)
                return@BasicTextField
            }
            if (!nv.text.startsWith(ZW)) {
                val clean = nv.withoutSentinel()
                if (clean.text == b.text) {
                    editor.backspaceAtStart(b)
                    return@BasicTextField
                }
                editor.onText(b, clean)
            } else {
                editor.onText(b, nv.withoutSentinel())
            }
        },
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        visualTransformation = if (b.type == BlockType.CODE) VisualTransformation.None else transformation,
        keyboardOptions = KeyboardOptions(
            capitalization = if (b.type == BlockType.CODE) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = spellCheck && b.type != BlockType.CODE,
        ),
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    editor.focusedId = b.id
                    editor.lastFocusedId = b.id
                } else if (editor.focusedId == b.id) {
                    editor.focusedId = null
                }
            }
            .onPreviewKeyEvent { e -> handleKey(editor, b, e, onSlashPick, onShortcut) },
        decorationBox = { inner ->
            Box {
                val showHint = b.text.isEmpty() && (focused || (b.type != BlockType.TEXT && b.type != BlockType.CODE))
                if (showHint) {
                    Text(
                        if (b.type == BlockType.TEXT && editor.blocks.size == 1) "Schreibe etwas, tippe / für Befehle …" else b.type.hint,
                        style = style.copy(color = c.textMuted.copy(alpha = 0.6f), textDecoration = TextDecoration.None),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

private fun handleKey(
    editor: EditorState,
    b: EditBlock,
    e: KeyEvent,
    onSlashPick: (EditBlock) -> Boolean,
    onShortcut: (KeyEvent) -> Boolean,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ctrl = e.isCtrlPressed
    val shift = e.isShiftPressed
    val alt = e.isAltPressed
    val slash = editor.slashFor == b.id
    val v = b.value
    when (e.key) {
        Key.DirectionDown -> {
            if (slash) { editor.slashIndex++; return true }
            if (alt) { editor.move(b, +1); return true }
            if (v.selection.collapsed && v.selection.start == v.text.length && !v.text.contains('\n')) return editor.focusNeighbour(b, +1)
        }
        Key.DirectionUp -> {
            if (slash) { editor.slashIndex = (editor.slashIndex - 1).coerceAtLeast(0); return true }
            if (alt) { editor.move(b, -1); return true }
            if (v.selection.collapsed && v.selection.start == 0) return editor.focusNeighbour(b, -1)
        }
        Key.DirectionLeft -> if (!shift && v.selection.collapsed && v.selection.start == 0) return editor.focusNeighbour(b, -1)
        Key.DirectionRight -> if (!shift && v.selection.collapsed && v.selection.start == v.text.length) {
            editor.focusNeighbour(b, +1); return true
        }
        Key.Enter, Key.NumPadEnter -> {
            if (slash) return onSlashPick(b)
            if (ctrl) {
                when (b.type) {
                    BlockType.TODO -> editor.toggleChecked(b)
                    BlockType.TOGGLE -> editor.toggleCollapsed(b)
                    else -> editor.insertBlocks(listOf(app.voidnotes.data.Block()), afterId = b.id)
                }
                return true
            }
            if (shift) { editor.insertText(b, "\n"); return true }
            if (b.type == BlockType.CODE) { editor.insertText(b, "\n"); return true }
        }
        Key.Tab -> {
            if (shift) editor.outdent(b) else if (b.type == BlockType.CODE) editor.insertText(b, "    ") else editor.indent(b)
            return true
        }
        Key.Escape -> if (slash) { editor.slashFor = null; return true }
        Key.B -> if (ctrl) { editor.wrap(b, "**"); return true }
        Key.I -> if (ctrl) { editor.wrap(b, "*"); return true }
        Key.E -> if (ctrl && !shift) { editor.wrap(b, "`"); return true }
        Key.S -> if (ctrl && shift) { editor.wrap(b, "~~"); return true }
        Key.H -> if (ctrl && shift) { editor.wrap(b, "=="); return true }
        Key.D -> if (ctrl) { editor.duplicate(b); return true }
        Key.Z -> if (ctrl) { if (shift) editor.redo() else editor.undo(); return true }
        Key.Y -> if (ctrl) { editor.redo(); return true }
        Key.Delete -> if (ctrl && shift) { editor.delete(b); return true }
        Key.One, Key.Two, Key.Three -> if (ctrl && alt) {
            editor.setType(b, when (e.key) { Key.One -> BlockType.H1; Key.Two -> BlockType.H2; else -> BlockType.H3 }); return true
        }
        Key.Zero -> if (ctrl && alt) { editor.setType(b, BlockType.TEXT); return true }
        else -> {}
    }
    return onShortcut(e)
}

// ------------------------------------------------------------------ markers in front of blocks

@Composable
fun Marker(editor: EditorState, b: EditBlock, number: Int, scale: Float) {
    val c = VoidTheme.colors
    val lineH = (blockStyle(b.type, c, scale, false).lineHeight.value).dp
    Box(Modifier.width(28.dp).height(lineH), contentAlignment = Alignment.Center) {
        when (b.type) {
            BlockType.BULLET -> {
                val shape = b.level % 3
                Box(
                    Modifier.size(6.dp).clip(if (shape == 2) RoundedCornerShape(1.dp) else CircleShape)
                        .background(if (shape == 1) Color.Transparent else c.text)
                        .border(1.2.dp, c.text, if (shape == 2) RoundedCornerShape(1.dp) else CircleShape),
                )
            }
            BlockType.NUMBERED -> Text(
                if (b.level % 2 == 1) "${('a' + ((number - 1) % 26))}." else "$number.",
                style = blockStyle(BlockType.TEXT, c, scale, false).copy(fontFamily = MonoFont, fontSize = (13 * scale).sp, color = c.textMuted),
            )
            BlockType.TODO -> Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (b.checked) c.accent else Color.Transparent)
                    .border(1.5.dp, if (b.checked) c.accent else c.textMuted, RoundedCornerShape(6.dp))
                    .clickable { editor.toggleChecked(b) },
                contentAlignment = Alignment.Center,
            ) {
                if (b.checked) Icon(Icons.Outlined.Check, "Erledigt", Modifier.size(14.dp), tint = c.onAccent)
            }
            BlockType.TOGGLE -> Box(
                Modifier.size(26.dp).clip(CircleShape).clickable { editor.toggleCollapsed(b) },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (b.collapsed) "▸" else "▾", color = c.text, fontSize = 16.sp)
            }
            else -> {}
        }
    }
}

// ------------------------------------------------------------------ handle & block menu

@Composable
fun BlockHandle(visible: Boolean, items: () -> List<MenuItem?>) {
    val c = VoidTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(Modifier.width(26.dp)) {
        Box(
            Modifier.padding(top = 2.dp).size(24.dp).clip(RoundedCornerShape(6.dp)).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            if (visible || open) Icon(Icons.Outlined.DragIndicator, "Block-Menü", Modifier.size(18.dp), tint = c.textMuted)
        }
        VoidMenu(open, { open = false }, if (open) items() else emptyList())
    }
}

// ------------------------------------------------------------------ attachments

private val imageCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

@Composable
fun ImageBlock(file: File, name: String, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = VoidTheme.colors
    val key = file.absolutePath
    val bmp by produceState<Bitmap?>(imageCache.get(key), key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) { PdfWriter.decode(file, 1600)?.also { imageCache.put(key, it) } }
    }
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
            .onRightClick(onMenu)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val img = bmp
        if (img != null) {
            Image(
                img.asImageBitmap(), name,
                Modifier.fillMaxWidth().heightIn(max = 520.dp).aspectRatio(img.width.toFloat() / img.height.coerceAtLeast(1)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Image, null, tint = c.textMuted)
                Spacer(Modifier.width(10.dp))
                Text(if (file.exists()) "Bild wird geladen …" else "Bild fehlt: $name", color = c.textMuted)
            }
        }
    }
}

private fun iconForMime(mime: String, name: String): ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" -> Icons.Outlined.PictureAsPdf
        mime.startsWith("image/") -> Icons.Outlined.Image
        mime.startsWith("audio/") -> Icons.Outlined.AudioFile
        mime.startsWith("video/") -> Icons.Outlined.Movie
        ext in setOf("xls", "xlsx", "csv", "ods") -> Icons.Outlined.TableChart
        ext in setOf("doc", "docx", "odt", "txt", "md", "rtf") -> Icons.Outlined.Description
        ext in setOf("zip", "7z", "rar", "tar", "gz") -> Icons.Outlined.FolderZip
        else -> Icons.Outlined.InsertDriveFile
    }
}

@Composable
fun FileBlock(name: String, mime: String, size: Long, missing: Boolean, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = VoidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(if (hovered) c.surfaceHigh else c.surface)
            .border(1.dp, c.divider, RoundedCornerShape(14.dp))
            .hoverable(interaction)
            .onRightClick(onMenu)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(iconForMime(mime, name), null, Modifier.size(20.dp), tint = c.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (missing) "Datei fehlt" else listOf(formatSize(size), name.substringAfterLast('.', "").uppercase()).filter { it.isNotBlank() }.joinToString(" · "),
                color = c.textMuted, style = MaterialTheme.typography.labelSmall,
            )
        }
        Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp), tint = c.textMuted)
    }
}

@Composable
fun PageLinkBlock(page: Page?, onOpen: () -> Unit, onMenu: () -> Unit) {
    val c = VoidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val gone = page == null || page.inTrash
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (hovered) c.surface else Color.Transparent)
            .hoverable(interaction)
            .onRightClick(onMenu)
            .clickable(onClick = { if (gone) onMenu() else onOpen() })
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageIcon(page, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                page == null -> "Gelöschte Seite"
                page.inTrash -> page.displayTitle + " (im Papierkorb)"
                else -> page.displayTitle
            },
            color = if (gone) c.textMuted else c.text,
            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (gone) TextDecoration.LineThrough else TextDecoration.Underline),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DividerBlock(onMenu: () -> Unit) {
    val c = VoidTheme.colors
    Box(Modifier.fillMaxWidth().height(26.dp).onRightClick(onMenu).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(48) { Box(Modifier.size(2.dp).clip(CircleShape).background(c.textMuted.copy(alpha = 0.45f))) }
        }
    }
}
