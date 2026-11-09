package app.voidnotes.ui.editor

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import app.voidnotes.data.AppSettings
import app.voidnotes.data.Block
import app.voidnotes.data.BlockType
import app.voidnotes.data.Page
import app.voidnotes.data.Placeholders
import app.voidnotes.data.Template
import app.voidnotes.ui.AppActions
import app.voidnotes.ui.Dialog
import app.voidnotes.ui.Label
import app.voidnotes.ui.LocalActions
import app.voidnotes.ui.MenuItem
import app.voidnotes.ui.NotesViewModel
import app.voidnotes.ui.Pill
import app.voidnotes.ui.RoundButton
import app.voidnotes.ui.Screen
import app.voidnotes.ui.Separator
import app.voidnotes.ui.VoidMenu
import app.voidnotes.ui.findActivity
import app.voidnotes.ui.pageMenu
import app.voidnotes.ui.relativeTime
import app.voidnotes.ui.theme.VoidTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** An entry of the "/" menu and the "+" insert menu. */
data class InsertItem(val label: String, val keywords: String, val icon: ImageVector, val run: (EditBlock?) -> Unit)

private fun typeIcon(t: BlockType): ImageVector = when (t) {
    BlockType.TEXT -> Icons.Outlined.Notes
    BlockType.H1, BlockType.H2, BlockType.H3 -> Icons.Outlined.Title
    BlockType.BULLET -> Icons.AutoMirrored.Outlined.FormatListBulleted
    BlockType.NUMBERED -> Icons.Outlined.FormatListNumbered
    BlockType.TODO -> Icons.Outlined.CheckBox
    BlockType.TOGGLE -> Icons.Outlined.UnfoldMore
    BlockType.QUOTE -> Icons.Outlined.FormatQuote
    BlockType.CALLOUT -> Icons.Outlined.Lightbulb
    BlockType.CODE -> Icons.Outlined.Code
    BlockType.DIVIDER -> Icons.Outlined.HorizontalRule
    BlockType.IMAGE -> Icons.Outlined.Image
    BlockType.FILE -> Icons.Outlined.InsertDriveFile
    BlockType.PAGE -> Icons.Outlined.Article
}

private val typeKeywords = mapOf(
    BlockType.TEXT to "text absatz paragraph",
    BlockType.H1 to "h1 überschrift heading titel",
    BlockType.H2 to "h2 überschrift heading",
    BlockType.H3 to "h3 überschrift heading",
    BlockType.BULLET to "liste aufzählung bullet punkt",
    BlockType.NUMBERED to "nummer liste numbered zahl",
    BlockType.TODO to "todo checkbox aufgabe check haken",
    BlockType.TOGGLE to "toggle aufklappbar einklappen",
    BlockType.QUOTE to "zitat quote",
    BlockType.CALLOUT to "hinweis callout info box",
    BlockType.CODE to "code programm",
    BlockType.DIVIDER to "trennlinie linie divider hr",
)

private fun today(pattern: String) = SimpleDateFormat(pattern, Locale.GERMAN).format(Date())

/** Everything that can be inserted; type items convert the current block. */
fun insertItems(vm: NotesViewModel, editor: EditorState, actions: AppActions, convert: Boolean): List<InsertItem> {
    val types = listOf(
        BlockType.TEXT, BlockType.H1, BlockType.H2, BlockType.H3, BlockType.TODO, BlockType.BULLET, BlockType.NUMBERED,
        BlockType.TOGGLE, BlockType.QUOTE, BlockType.CALLOUT, BlockType.CODE, BlockType.DIVIDER,
    ).map { t ->
        InsertItem(t.label, typeKeywords[t].orEmpty(), typeIcon(t)) { b ->
            if (b != null && convert && (b.type.hasText)) editor.setType(b, t)
            else editor.insertBlocks(listOf(Block(type = t, emoji = if (t == BlockType.CALLOUT) "💡" else null)), afterId = b?.id)
        }
    }
    val extra = listOfNotNull(
        if (editor.target is EditTarget.PageTarget) InsertItem("Unterseite", "seite page unterseite", Icons.AutoMirrored.Outlined.NoteAdd) {
            vm.newSubPage(editor.target.id)
        } else null,
        InsertItem("Bild", "bild foto image galerie", Icons.Outlined.Image) { actions.attachImages() },
        InsertItem("Foto aufnehmen", "kamera foto camera", Icons.Outlined.PhotoCamera) { actions.takePhoto() },
        InsertItem("Datei", "datei anhang file pdf", Icons.Outlined.AttachFile) { actions.attachFiles() },
        InsertItem("Datum", "datum heute date", Icons.Outlined.Event) { b ->
            if (b != null && b.type.hasText) editor.insertText(b, today("dd.MM.yyyy")) else editor.insertBlocks(listOf(Block(text = today("dd.MM.yyyy"))))
        },
        InsertItem("Datum & Uhrzeit", "zeit uhrzeit time jetzt", Icons.Outlined.Event) { b ->
            val s = today("dd.MM.yyyy, HH:mm")
            if (b != null && b.type.hasText) editor.insertText(b, s) else editor.insertBlocks(listOf(Block(text = s)))
        },
    )
    return types + extra
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun EditorScreen(
    vm: NotesViewModel,
    editor: EditorState,
    pages: Map<String, Page>,
    templates: List<Template>,
    settings: AppSettings,
    wide: Boolean,
) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val page = (editor.target as? EditTarget.PageTarget)?.let { pages[it.id] }
    val template = (editor.target as? EditTarget.TemplateTarget)?.let { t -> templates.firstOrNull { it.id == t.id } }
    val scale = settings.textSize.scale
    val maxWidth = if (page?.wide == true) 100_000.dp else settings.pageWidth.dp.dp

    BackHandler(enabled = editor.slashFor != null) { editor.slashFor = null }

    val dropTarget = remember(editor) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val drag = event.toAndroidDragEvent()
                val clip: ClipData = drag.clipData ?: return false
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                if (uris.isNotEmpty()) {
                    runCatching { context.findActivity()?.requestDragAndDropPermissions(drag) }
                    vm.attach(uris)
                    return true
                }
                val text = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).text?.toString() }.joinToString("\n")
                if (text.isBlank()) return false
                val parsed = app.voidnotes.data.MarkdownIO.import(text, extractTitle = false).second
                editor.insertBlocks(parsed)
                return true
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.background)
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
    ) {
        EditorTopBar(vm, editor, page, template, actions, wide, settings)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = if (wide) 40.dp else 14.dp),
                ) {
                    Spacer(Modifier.height(if (wide) 36.dp else 12.dp))
                    PageHeader(vm, editor, page, template, scale)
                    Spacer(Modifier.height(10.dp))
                    BlockList(vm, editor, pages, settings, actions)
                }
                // Tap below the last block to keep writing.
                Box(
                    Modifier.fillMaxWidth().height(260.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { editor.focusEnd() },
                )
            }
        }
        if (editor.focusedId != null) {
            EditorToolbar(vm, editor, actions, Modifier.imePadding().navigationBarsPadding()) { focusManager.clearFocus() }
        } else {
            Spacer(Modifier.navigationBarsPadding().imePadding())
        }
    }
}

@Composable
private fun EditorTopBar(
    vm: NotesViewModel,
    editor: EditorState,
    page: Page?,
    template: Template?,
    actions: AppActions,
    wide: Boolean,
    settings: AppSettings,
) {
    val c = VoidTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (wide) {
            RoundButton(Icons.Outlined.ViewSidebar, "Seitenleiste", { vm.editSettings { it.copy(sidebar = !it.sidebar) } }, size = 40.dp)
        }
        if (!wide || vm.stack.size > 2) RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", { vm.back() }, size = 40.dp)
        Spacer(Modifier.width(6.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (page != null) {
                vm.ancestors(page.id).forEach { a ->
                    Text(
                        (a.icon + " " + a.displayTitle).trim(), color = c.textMuted, maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.openPage(a.id) }.padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                    Text("/", color = c.textMuted.copy(alpha = 0.5f))
                }
                Text(
                    (editor.icon + " " + editor.title.text.ifBlank { "Ohne Titel" }).trim(), color = c.text, maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 6.dp),
                )
            } else if (template != null) {
                Label("Vorlage", color = c.accent)
                Spacer(Modifier.width(8.dp))
                Text(template.name, color = c.text, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (page?.share != null) {
            val session = vm.sync?.takeIf { it.pageId == page.id }
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.divider, RoundedCornerShape(50))
                    .clickable { vm.dialog = Dialog.Share(page.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.voidnotes.ui.SyncDot(session?.state ?: app.voidnotes.data.SyncState.OFFLINE)
                Spacer(Modifier.width(8.dp))
                Label(session?.state?.label ?: "Geteilt", color = if (session?.state == app.voidnotes.data.SyncState.ERROR) c.accent else c.text)
            }
            Spacer(Modifier.width(4.dp))
        }
        if (editor.canUndo) RoundButton(Icons.AutoMirrored.Outlined.Undo, "Rückgängig", { editor.undo() }, size = 40.dp)
        if (page != null) {
            RoundButton(
                if (page.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, "Favorit",
                { vm.toggleFavorite(page.id) }, size = 40.dp, tint = if (page.favorite) c.accent else null,
            )
            if (wide) {
                RoundButton(Icons.Outlined.Print, "Drucken", { actions.print(page.id) }, size = 40.dp)
                RoundButton(Icons.Outlined.Share, "Teilen", { actions.sharePdf(page.id) }, size = 40.dp)
            }
        } else if (template != null) {
            Pill("Seite erstellen", selected = true, onClick = { vm.commitEditor(); vm.newPage(null, vm.store.templates.value.firstOrNull { it.id == template.id }) })
            Spacer(Modifier.width(4.dp))
        }
        Box {
            RoundButton(Icons.Outlined.MoreVert, "Mehr", { menu = true }, size = 40.dp)
            val items: List<MenuItem?> = when {
                page != null -> listOfNotNull(
                    if (editor.canRedo) MenuItem("Wiederholen", Icons.AutoMirrored.Outlined.Redo) { editor.redo() } else null,
                    MenuItem("Datei anhängen", Icons.Outlined.AttachFile) { actions.attachFiles() },
                    MenuItem("Bild einfügen", Icons.Outlined.Image) { actions.attachImages() },
                    MenuItem("Foto aufnehmen", Icons.Outlined.PhotoCamera) { actions.takePhoto() },
                    Separator,
                    MenuItem("PDF teilen", Icons.Outlined.Share) { actions.sharePdf(page.id) },
                    MenuItem("Als Markdown teilen", Icons.Outlined.Share) { actions.shareMarkdown(page.id) },
                    MenuItem("Als Text teilen", Icons.Outlined.Share) { actions.shareText(page.id) },
                    MenuItem("Als Markdown exportieren", Icons.Outlined.Notes) { actions.exportMarkdown(page.id) },
                    MenuItem(if (page.wide) "Normale Breite" else "Volle Breite", Icons.Outlined.OpenInFull) { vm.toggleWide(page.id) },
                    Separator,
                ) + pageMenu(vm, actions, page, inEditor = true)
                template != null -> listOf(
                    MenuItem("Name & Beschreibung", Icons.Outlined.DriveFileRenameOutline) { vm.dialog = Dialog.TemplateInfo(template.id) },
                    MenuItem("Symbol ändern", Icons.Outlined.ViewQuilt) { vm.dialog = Dialog.EditorIcon },
                    MenuItem("Datei anhängen", Icons.Outlined.AttachFile) { actions.attachFiles() },
                    MenuItem("Vorlage duplizieren", Icons.Outlined.ContentCopy) { vm.duplicateTemplate(template.id) },
                    null,
                    MenuItem("Vorlage löschen", Icons.Outlined.DeleteOutline, danger = true) { vm.dialog = Dialog.DeleteTemplate(template.id) },
                )
                else -> emptyList()
            }
            VoidMenu(menu, { menu = false }, items)
        }
    }
}

@Composable
private fun PageHeader(vm: NotesViewModel, editor: EditorState, page: Page?, template: Template?, scale: Float) {
    val c = VoidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val openIcon = { vm.dialog = if (page != null) Dialog.Icon(page.id) else Dialog.EditorIcon }
    Column(Modifier.fillMaxWidth().hoverable(interaction).padding(start = 26.dp)) {
        if (editor.icon.isNotBlank()) {
            Text(
                editor.icon, fontSize = 52.sp, lineHeight = 64.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = openIcon).padding(4.dp),
            )
        } else {
            Text(
                "☺  Symbol hinzufügen", color = c.textMuted.copy(alpha = if (hovered || editor.focusedId == TITLE) 0.9f else 0.45f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = openIcon).padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        TitleField(editor, scale)
        Spacer(Modifier.height(6.dp))
        when {
            page != null -> Label("Bearbeitet ${relativeTime(page.updated)}")
            template != null -> Label("Vorlage · Platzhalter: " + Placeholders.help.joinToString("  ") { it.first })
        }
    }
}

@Composable
private fun TitleField(editor: EditorState, scale: Float) {
    val c = VoidTheme.colors
    val requester = remember { FocusRequester() }
    val req = editor.focusRequest
    LaunchedEffect(req) {
        if (req == null || req.id != TITLE) return@LaunchedEffect
        val off = if (req.offset < 0) editor.title.text.length else req.offset.coerceIn(0, editor.title.text.length)
        editor.title = editor.title.copy(selection = androidx.compose.ui.text.TextRange(off))
        androidx.compose.runtime.withFrameNanos { }
        runCatching { requester.requestFocus() }
        if (editor.focusRequest == req) editor.focusRequest = null
    }
    val style = TextStyle(color = c.text, fontSize = (34 * scale).sp, lineHeight = (42 * scale).sp, fontWeight = FontWeight.Bold)
    BasicTextField(
        value = editor.title,
        onValueChange = { editor.onTitle(it) },
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged {
            if (it.isFocused) editor.focusedId = TITLE else if (editor.focusedId == TITLE) editor.focusedId = null
        },
        decorationBox = { inner ->
            Box {
                if (editor.title.text.isEmpty()) Text("Ohne Titel", style = style.copy(color = c.textMuted.copy(alpha = 0.45f)))
                inner()
            }
        },
    )
}

/** Number of each numbered item, by position in [blocks]. */
private fun numbers(blocks: List<EditBlock>): IntArray {
    val out = IntArray(blocks.size)
    val counters = IntArray(16)
    blocks.forEachIndexed { i, b ->
        val lvl = b.level.coerceIn(0, 15)
        if (b.type == BlockType.NUMBERED) {
            counters[lvl]++
            out[i] = counters[lvl]
            for (d in lvl + 1 until 16) counters[d] = 0
        } else for (d in lvl until 16) counters[d] = 0
    }
    return out
}

@Composable
private fun BlockList(vm: NotesViewModel, editor: EditorState, pages: Map<String, Page>, settings: AppSettings, actions: AppActions) {
    val all = editor.blocks.toList()
    val nums = numbers(all)
    val visible = editor.visibleBlocks()
    val numberOf = HashMap<String, Int>(all.size).apply { all.forEachIndexed { i, b -> put(b.id, nums[i]) } }
    visible.forEach { b ->
        androidx.compose.runtime.key(b.id) {
            BlockRow(vm, editor, b, numberOf[b.id] ?: 0, pages, settings, actions)
        }
    }
}

@Composable
private fun BlockRow(
    vm: NotesViewModel,
    editor: EditorState,
    b: EditBlock,
    number: Int,
    pages: Map<String, Page>,
    settings: AppSettings,
    actions: AppActions,
) {
    val c = VoidTheme.colors
    val scale = settings.textSize.scale
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }

    val blockMenu: () -> List<MenuItem?> = {
        val turnInto = if (b.type.hasText) listOf(
            BlockType.TEXT, BlockType.H1, BlockType.H2, BlockType.H3, BlockType.TODO, BlockType.BULLET,
            BlockType.NUMBERED, BlockType.TOGGLE, BlockType.QUOTE, BlockType.CALLOUT, BlockType.CODE,
        ).filter { it != b.type }.map { t -> MenuItem("In ${t.label}", typeIcon(t)) { editor.setType(b, t) } } + listOf(null) else emptyList()
        val fileItems = if (b.file != null) listOf(
            MenuItem("Öffnen", Icons.AutoMirrored.Outlined.OpenInNew) { actions.openAttachment(b.file!!, b.mime.orEmpty(), b.fileName.orEmpty()) },
            MenuItem("Teilen", Icons.Outlined.Share) { actions.shareAttachment(b.file!!, b.mime.orEmpty(), b.fileName.orEmpty()) },
            MenuItem("Umbenennen", Icons.Outlined.DriveFileRenameOutline) { vm.dialog = Dialog.RenameFile(b.id) },
            null,
        ) else emptyList()
        val calloutItems = if (b.type == BlockType.CALLOUT) listOf(MenuItem("Symbol ändern", Icons.Outlined.Lightbulb) { vm.dialog = Dialog.CalloutEmoji(b.id) }) else emptyList()
        fileItems + calloutItems + turnInto + listOf(
            MenuItem("Nach oben", Icons.Outlined.ArrowUpward) { editor.move(b, -1) },
            MenuItem("Nach unten", Icons.Outlined.ArrowDownward) { editor.move(b, +1) },
            MenuItem("Einrücken", Icons.AutoMirrored.Outlined.FormatIndentIncrease) { editor.indent(b) },
            MenuItem("Ausrücken", Icons.AutoMirrored.Outlined.FormatIndentDecrease, enabled = b.level > 0) { editor.outdent(b) },
            MenuItem("Duplizieren", Icons.Outlined.ContentCopy) { editor.duplicate(b) },
            null,
            MenuItem("Löschen", Icons.Outlined.DeleteOutline, danger = true) { editor.delete(b) },
        )
    }

    val topPad = when (b.type) {
        BlockType.H1 -> 18.dp
        BlockType.H2 -> 14.dp
        BlockType.H3 -> 10.dp
        else -> 1.dp
    }
    Box {
        Row(
            Modifier.fillMaxWidth().hoverable(interaction).padding(top = topPad, bottom = 1.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BlockHandle(visible = hovered || editor.focusedId == b.id, items = blockMenu)
            Spacer(Modifier.width((b.level * 26).dp))
            when (b.type) {
                BlockType.BULLET, BlockType.NUMBERED, BlockType.TODO, BlockType.TOGGLE -> {
                    Marker(editor, b, number, scale)
                    Spacer(Modifier.width(4.dp))
                    TextBlock(vm, editor, b, settings, actions, Modifier.weight(1f))
                }
                BlockType.QUOTE -> Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(c.text))
                    Spacer(Modifier.width(14.dp))
                    TextBlock(vm, editor, b, settings, actions, Modifier.weight(1f).padding(vertical = 2.dp))
                }
                BlockType.CALLOUT -> Row(
                    Modifier.weight(1f).padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(
                        b.emoji ?: "💡", fontSize = (20 * scale).sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.dialog = Dialog.CalloutEmoji(b.id) },
                    )
                    Spacer(Modifier.width(10.dp))
                    TextBlock(vm, editor, b, settings, actions, Modifier.weight(1f))
                }
                BlockType.CODE -> Box(
                    Modifier.weight(1f).padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    TextBlock(vm, editor, b, settings, actions, Modifier.fillMaxWidth())
                }
                BlockType.DIVIDER -> Box(Modifier.weight(1f)) { DividerBlock { menu = true } }
                BlockType.IMAGE -> Box(Modifier.weight(1f)) {
                    val f = b.file?.let { vm.store.attachment(it) }
                    if (f != null) ImageBlock(f, b.fileName.orEmpty(), onOpen = { actions.openAttachment(b.file!!, b.mime ?: "image/*", b.fileName.orEmpty()) }, onMenu = { menu = true })
                }
                BlockType.FILE -> Box(Modifier.weight(1f)) {
                    @Suppress("UNUSED_VARIABLE") val version = app.voidnotes.data.Store.fileVersion
                    val missing = b.file?.let { !vm.store.attachment(it).exists() } ?: true
                    FileBlock(b.fileName ?: "Datei", b.mime.orEmpty(), b.size, missing, onOpen = {
                        b.file?.let { actions.openAttachment(it, b.mime.orEmpty(), b.fileName.orEmpty()) }
                    }, onMenu = { menu = true })
                }
                BlockType.PAGE -> Box(Modifier.weight(1f)) {
                    PageLinkBlock(pages[b.pageId], onOpen = { b.pageId?.let { vm.openPage(it) } }, onMenu = { menu = true })
                }
                else -> TextBlock(vm, editor, b, settings, actions, Modifier.weight(1f))
            }
        }
        VoidMenu(menu, { menu = false }, if (menu) blockMenu() else emptyList(), offset = DpOffset(30.dp, 0.dp))
        if (editor.slashFor == b.id) SlashMenu(vm, editor, b, actions)
    }
}

@Composable
private fun TextBlock(vm: NotesViewModel, editor: EditorState, b: EditBlock, settings: AppSettings, actions: AppActions, modifier: Modifier) {
    BlockField(
        editor, b, settings.textSize.scale, settings.spellCheck,
        onSlashPick = { blk ->
            val items = filteredSlash(vm, editor, blk, actions)
            val item = items.getOrNull(editor.slashIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
            if (item == null) {
                editor.slashFor = null; false
            } else {
                editor.clearSlash(blk)
                item.run(blk)
                true
            }
        },
        onShortcut = { e: KeyEvent -> false },
        modifier = modifier,
    )
}

private fun filteredSlash(vm: NotesViewModel, editor: EditorState, b: EditBlock, actions: AppActions): List<InsertItem> {
    val q = editor.slashQuery(b).trim().lowercase()
    val all = insertItems(vm, editor, actions, convert = true)
    if (q.isEmpty()) return all
    return all.filter { it.label.lowercase().contains(q) || it.keywords.contains(q) }
}

@Composable
private fun SlashMenu(vm: NotesViewModel, editor: EditorState, b: EditBlock, actions: AppActions) {
    val c = VoidTheme.colors
    val items = filteredSlash(vm, editor, b, actions)
    val selected = editor.slashIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    DropdownMenu(
        expanded = true,
        onDismissRequest = { editor.slashFor = null },
        properties = PopupProperties(focusable = false),
        offset = DpOffset(56.dp, 4.dp),
        containerColor = c.surfaceHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(min = 240.dp),
    ) {
        if (items.isEmpty()) {
            Text("Nichts gefunden", color = c.textMuted, modifier = Modifier.padding(16.dp))
        }
        Label("Blöcke", Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        items.forEachIndexed { i, item ->
            DropdownMenuItem(
                text = { Text(item.label, color = c.text) },
                leadingIcon = { Icon(item.icon, null, Modifier.size(20.dp), tint = if (i == selected) c.accent else c.textMuted) },
                modifier = Modifier.background(if (i == selected) c.selection else Color.Transparent),
                onClick = {
                    editor.clearSlash(b)
                    item.run(b)
                },
            )
        }
    }
}

@Composable
private fun EditorToolbar(vm: NotesViewModel, editor: EditorState, actions: AppActions, modifier: Modifier, hideKeyboard: () -> Unit) {
    val c = VoidTheme.colors
    val b = editor.focused
    var insert by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    val onTitle = editor.focusedId == TITLE
    Row(
        modifier.fillMaxWidth().background(c.surface).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ToolButton(Icons.Outlined.Add, "Einfügen", !onTitle) { insert = true }
                VoidMenu(
                    insert, { insert = false },
                    if (insert) insertItems(vm, editor, actions, convert = false).map { item -> MenuItem(item.label, item.icon) { item.run(editor.focused ?: editor.block(editor.lastFocusedId)) } } else emptyList(),
                )
            }
            if (b != null && b.type.hasText) {
                ToolButton(Icons.Outlined.CheckBox, "Checkbox", true, active = b.type == BlockType.TODO) {
                    editor.setType(b, if (b.type == BlockType.TODO) BlockType.TEXT else BlockType.TODO)
                }
                ToolButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Aufzählung", true, active = b.type == BlockType.BULLET) {
                    editor.setType(b, if (b.type == BlockType.BULLET) BlockType.TEXT else BlockType.BULLET)
                }
                ToolButton(Icons.Outlined.Title, "Überschrift", true, active = b.type in setOf(BlockType.H1, BlockType.H2, BlockType.H3)) {
                    editor.setType(
                        b, when (b.type) {
                            BlockType.H1 -> BlockType.H2
                            BlockType.H2 -> BlockType.H3
                            BlockType.H3 -> BlockType.TEXT
                            else -> BlockType.H1
                        },
                    )
                }
                ToolDivider()
                ToolButton(Icons.Outlined.FormatBold, "Fett", true) { editor.wrap(b, "**") }
                ToolButton(Icons.Outlined.FormatItalic, "Kursiv", true) { editor.wrap(b, "*") }
                ToolButton(Icons.Outlined.StrikethroughS, "Durchgestrichen", true) { editor.wrap(b, "~~") }
                ToolButton(Icons.Outlined.BorderColor, "Markieren", true) { editor.wrap(b, "==") }
                ToolButton(Icons.Outlined.Code, "Code", true) { editor.wrap(b, "`") }
                ToolDivider()
                ToolButton(Icons.AutoMirrored.Outlined.FormatIndentIncrease, "Einrücken", true) { editor.indent(b) }
                ToolButton(Icons.AutoMirrored.Outlined.FormatIndentDecrease, "Ausrücken", b.level > 0) { editor.outdent(b) }
                ToolButton(Icons.Outlined.ArrowUpward, "Nach oben", true) { editor.move(b, -1) }
                ToolButton(Icons.Outlined.ArrowDownward, "Nach unten", true) { editor.move(b, +1) }
                val url = app.voidnotes.data.Inline.urlAt(b.text, b.value.selection.start)
                if (url != null) ToolButton(Icons.AutoMirrored.Outlined.OpenInNew, "Link öffnen", true) { actions.openUrl(url) }
            }
            ToolDivider()
            Box {
                ToolButton(Icons.Outlined.AttachFile, "Anhängen", true) { attach = true }
                VoidMenu(
                    attach, { attach = false },
                    listOf(
                        MenuItem("Datei", Icons.Outlined.InsertDriveFile) { actions.attachFiles() },
                        MenuItem("Bild aus Galerie", Icons.Outlined.Image) { actions.attachImages() },
                        MenuItem("Foto aufnehmen", Icons.Outlined.PhotoCamera) { actions.takePhoto() },
                    ),
                )
            }
            ToolButton(Icons.AutoMirrored.Outlined.Undo, "Rückgängig", editor.canUndo) { editor.undo() }
            ToolButton(Icons.AutoMirrored.Outlined.Redo, "Wiederholen", editor.canRedo) { editor.redo() }
        }
        ToolButton(Icons.Outlined.KeyboardHide, "Tastatur schließen", true, onClick = hideKeyboard)
    }
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, enabled: Boolean, active: Boolean = false, onClick: () -> Unit) {
    val c = VoidTheme.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (active) c.selection else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(21.dp), tint = when {
            !enabled -> c.textMuted.copy(alpha = 0.35f)
            active -> c.accent
            else -> c.text
        })
    }
}

@Composable
private fun ToolDivider() {
    val c = VoidTheme.colors
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(c.divider))
}

/** Used by the root to show the screen name in the window. */
fun screenTitle(s: Screen): String = when (s) {
    Screen.Home -> "Start"
    is Screen.PageScreen -> "Seite"
    is Screen.TemplateScreen -> "Vorlage"
    Screen.Search -> "Suche"
    Screen.Templates -> "Vorlagen"
    Screen.Trash -> "Papierkorb"
    Screen.Settings -> "Einstellungen"
}
