package app.voidnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.voidnotes.BuildConfig
import app.voidnotes.data.Accent
import app.voidnotes.data.AppSettings
import app.voidnotes.data.Inline
import app.voidnotes.data.NewPageMode
import app.voidnotes.data.Page
import app.voidnotes.data.PageWidth
import app.voidnotes.data.Template
import app.voidnotes.data.TextSize
import app.voidnotes.data.ThemeMode
import app.voidnotes.data.Updater
import app.voidnotes.ui.theme.VoidTheme
import app.voidnotes.ui.theme.headingStyle

/** Common frame of the list screens: back button, dot headline, centred column. */
@Composable
fun ScreenFrame(
    vm: NotesViewModel,
    title: String,
    wide: Boolean,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        DotGrid(Modifier.fillMaxSize().padding(top = 0.dp), spacing = 24.dp)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = if (wide) 40.dp else 16.dp, end = if (wide) 40.dp else 16.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().statusBarsPadding()) {
                    if (!wide) {
                        TabBar(vm, compact = false, modifier = Modifier.padding(top = 12.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = if (wide) 36.dp else 22.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!wide) {
                            RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", { vm.back() }, size = 40.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Headline(title, Modifier.weight(1f), size = if (wide) 40 else 32)
                        actions()
                    }
                }
            }
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

/** Centred content row of [ScreenFrame]. */
@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.widthIn(max = 900.dp).fillMaxWidth()) { content() }
}

// ------------------------------------------------------------------ search

private data class Hit(val page: Page, val snippet: AnnotatedString?, val titleHit: Boolean)

@Composable
fun SearchScreen(vm: NotesViewModel, pages: Map<String, Page>, wide: Boolean) {
    val c = VoidTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = vm.searchQuery.trim()
    val hits = remember(q, pages) { search(pages, q, c.accent) }
    ScreenFrame(vm, "Suche", wide) {
        item {
            Centered {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = c.textMuted)
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        vm.searchQuery, { vm.searchQuery = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner -> Box { if (vm.searchQuery.isEmpty()) Text("Titel und Inhalte durchsuchen …", color = c.textMuted); inner() } },
                    )
                    if (vm.searchQuery.isNotEmpty()) {
                        Icon(Icons.Outlined.Close, "Leeren", Modifier.clip(CircleShape).clickable { vm.searchQuery = "" }.padding(4.dp), tint = c.textMuted)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { Centered { Label(if (q.isEmpty()) "Zuletzt geöffnet" else "${hits.size} Treffer") } }
        item { Spacer(Modifier.height(6.dp)) }
        if (q.isNotEmpty() && hits.isEmpty()) {
            item { EmptyState("Nichts gefunden", "Kein Titel und kein Text enthält „$q“.") }
        }
        items(hits, key = { it.page.id }) { h ->
            Centered {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.openPage(h.page.id) }.padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PageIcon(h.page, 26.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.page.displayTitle, color = c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val path = vm.ancestors(h.page.id).joinToString(" / ") { it.displayTitle }
                        if (path.isNotEmpty()) Label(path)
                        h.snippet?.let { Text(it, color = c.textMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    Label(relativeTime(h.page.updated))
                }
            }
        }
    }
}

private fun search(pages: Map<String, Page>, q: String, accent: Color): List<Hit> {
    val live = pages.values.filterNot { it.inTrash }
    if (q.isEmpty()) return live.filter { it.opened > 0 }.sortedByDescending { it.opened }.take(15).map { Hit(it, null, false) }
    val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return live.mapNotNull { p ->
        val title = p.title.lowercase()
        val texts = p.blocks.map { Inline.plain(it.text) + " " + (it.fileName ?: "") }
        val all = (listOf(title) + texts.map { it.lowercase() }).joinToString("\n")
        if (!words.all { all.contains(it) }) return@mapNotNull null
        val titleHit = words.all { title.contains(it) }
        val line = texts.firstOrNull { t -> words.any { t.lowercase().contains(it) } }
        val snippet = line?.let { highlight(it, words, accent) }
        Hit(p, snippet, titleHit)
    }.sortedWith(compareByDescending<Hit> { it.titleHit }.thenByDescending { it.page.updated })
}

private fun highlight(text: String, words: List<String>, accent: Color): AnnotatedString {
    val lower = text.lowercase()
    val first = words.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull() ?: 0
    val start = (first - 40).coerceAtLeast(0)
    val cut = (if (start > 0) "…" else "") + text.substring(start).replace('\n', ' ')
    val cutLower = cut.lowercase()
    return buildAnnotatedString {
        append(cut)
        words.forEach { w ->
            var i = cutLower.indexOf(w)
            while (i >= 0) {
                addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), i, i + w.length)
                i = cutLower.indexOf(w, i + w.length)
            }
        }
    }
}

// ------------------------------------------------------------------ templates

@Composable
fun TemplatesScreen(vm: NotesViewModel, templates: List<Template>, wide: Boolean) {
    val c = VoidTheme.colors
    ScreenFrame(vm, "Vorlagen", wide, actions = {
        Pill("Neue Vorlage", selected = true, onClick = { vm.newTemplate() }, icon = Icons.Outlined.Add)
    }) {
        item {
            Centered {
                Text(
                    "Tippe auf eine Vorlage, um daraus eine neue Seite zu erstellen. Eigene Vorlagen: Seite öffnen → ⋮ → „Als Vorlage speichern“ " +
                        "oder hier „Neue Vorlage“. Platzhalter wie {{datum}}, {{zeit}} oder {{wochentag}} werden beim Erstellen ersetzt.",
                    color = c.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        if (templates.isEmpty()) item { EmptyState("Keine Vorlagen", "Lege eine neue an oder stelle die Standardvorlagen wieder her.") }
        items(templates, key = { it.id }) { t ->
            var menu by remember { mutableStateOf(false) }
            Centered {
                Box {
                    TemplateRow(
                        t.icon, t.name, t.description.ifBlank { "${t.blocks.size} Blöcke" },
                        modifier = Modifier.onRightClick { menu = true },
                        trailing = {
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { vm.editTemplate(t.id) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Edit, "Bearbeiten", Modifier.size(20.dp), tint = c.textMuted)
                            }
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.MoreHoriz, "Menü", Modifier.size(20.dp), tint = c.textMuted)
                            }
                        },
                    ) { vm.newPage(null, t) }
                    VoidMenu(
                        menu, { menu = false },
                        listOf(
                            MenuItem("Neue Seite daraus", Icons.Outlined.Add) { vm.newPage(null, t) },
                            MenuItem("Inhalt bearbeiten", Icons.Outlined.Edit) { vm.editTemplate(t.id) },
                            MenuItem("Name & Beschreibung", Icons.Outlined.DriveFileRenameOutline) { vm.dialog = Dialog.TemplateInfo(t.id) },
                            MenuItem("Duplizieren", Icons.Outlined.ContentCopy) { vm.duplicateTemplate(t.id) },
                            MenuItem("Nach oben", Icons.Outlined.ArrowUpward) { vm.moveTemplate(t.id, -1) },
                            MenuItem("Nach unten", Icons.Outlined.ArrowDownward) { vm.moveTemplate(t.id, +1) },
                            null,
                            MenuItem("Löschen", Icons.Outlined.DeleteOutline, danger = true) { vm.dialog = Dialog.DeleteTemplate(t.id) },
                        ),
                    )
                }
            }
        }
        item {
            Centered {
                TextButton(onClick = { vm.restoreTemplates() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("STANDARDVORLAGEN WIEDERHERSTELLEN", style = MaterialTheme.typography.labelLarge, color = c.accent)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ trash

@Composable
fun TrashScreen(vm: NotesViewModel, pages: Map<String, Page>, wide: Boolean) {
    val c = VoidTheme.colors
    val trashed = pages.values.filter { p -> p.inTrash && (p.parentId == null || pages[p.parentId]?.inTrash != true) }
        .sortedByDescending { it.trashed }
    ScreenFrame(vm, "Papierkorb", wide, actions = {
        if (trashed.isNotEmpty()) Pill("Leeren", selected = false, onClick = { vm.dialog = Dialog.EmptyTrash }, icon = Icons.Outlined.DeleteForever)
    }) {
        item {
            Centered {
                Label("Seiten werden nach 30 Tagen automatisch gelöscht", Modifier.padding(bottom = 10.dp))
            }
        }
        if (trashed.isEmpty()) item { EmptyState("Leer", "Gelöschte Seiten landen hier und lassen sich wiederherstellen.") }
        items(trashed, key = { it.id }) { p ->
            val subCount = vm.descendants(p.id, pages).count { it.inTrash }
            Centered {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageIcon(p, 26.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayTitle, color = c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Label("Gelöscht ${relativeTime(p.trashed)}" + if (subCount > 0) " · $subCount Unterseiten" else "")
                    }
                    RoundButton(Icons.Outlined.Restore, "Wiederherstellen", { vm.restore(p.id) }, size = 40.dp)
                    RoundButton(Icons.Outlined.DeleteForever, "Endgültig löschen", { vm.dialog = Dialog.DeleteForever(p.id) }, size = 40.dp, tint = c.accent)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ settings

private val themeSwatches = mapOf(
    ThemeMode.BLACK to Color(0xFF000000),
    ThemeMode.GRAPHITE to Color(0xFF1C1C1E),
    ThemeMode.PAPER to Color(0xFFF7F6F3),
    ThemeMode.WHITE to Color(0xFFFFFFFF),
)

/** Categories of the settings, each on its own page (side by side on wide screens). */
enum class SettingsSection(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LOOK("Darstellung", "Design, Akzentfarbe, Schrift, Seitenbreite", Icons.Outlined.Palette),
    EDITOR("Schreiben", "Neue Seiten, Autokorrektur, Startseite", Icons.Outlined.EditNote),
    BACKUP("Sicherung", "Google-Sicherung, automatische ZIP", Icons.Outlined.CloudUpload),
    SHARE("Teilen & Zusammenarbeit", "Links, Firebase-Datenbank", Icons.Outlined.Link),
    IMPORT("Import", "Notion, Markdown", Icons.Outlined.Download),
    PDF("PDF & Drucken", "Symbol, Fußzeile", Icons.Outlined.Print),
    UPDATES("Updates", "Version, Changelog", Icons.Outlined.SystemUpdate),
    ABOUT("Über", "VOID Notes, Lizenzen", Icons.Outlined.Info),
}

@Composable
fun SettingsScreen(vm: NotesViewModel, s: AppSettings, wide: Boolean) {
    val c = VoidTheme.colors
    val section = vm.settingsSection
    androidx.activity.compose.BackHandler(enabled = !wide && section != null) { vm.settingsSection = null }
    Box(Modifier.fillMaxSize()) {
        DotGrid(Modifier.fillMaxSize(), spacing = 24.dp)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(horizontal = if (wide) 40.dp else 16.dp)) {
                if (!wide && section == null) TabBar(vm, compact = false, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(top = if (wide) 32.dp else 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) {
                        RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", {
                            if (section != null) vm.settingsSection = null else vm.back()
                        }, size = 40.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Headline(if (!wide && section != null) section.title else "Einstellungen", Modifier.weight(1f), size = if (wide) 40 else 30)
                }
            }
            if (wide) {
                val current = section ?: SettingsSection.LOOK
                Row(Modifier.weight(1f).padding(horizontal = 40.dp)) {
                    LazyColumn(Modifier.width(300.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SettingsSection.entries) { sec -> SectionRow(sec, sec == current) { vm.settingsSection = sec } }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                    Spacer(Modifier.width(28.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item { Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) { SectionContent(current, vm, s) } }
                        item { Spacer(Modifier.height(40.dp).navigationBarsPadding()) }
                    }
                }
            } else if (section == null) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SettingsSection.entries) { sec -> SectionRow(sec, false) { vm.settingsSection = sec } }
                    item {
                        Label("VOID Notes ${BuildConfig.VERSION_NAME}", Modifier.padding(top = 12.dp, start = 4.dp).navigationBarsPadding())
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { SectionContent(section, vm, s) } }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(sec: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val c = VoidTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.surfaceHigh else c.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.accent else c.divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (selected) c.accent else c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(sec.icon, null, Modifier.size(21.dp), tint = if (selected) c.onAccent else c.text)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(sec.title, color = c.text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(sec.subtitle, color = c.textMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textMuted)
    }
}

/** A titled group of settings on a raised card. */
@Composable
fun SettingsCard(title: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = VoidTheme.colors
    Column {
        if (title != null) {
            Label(title, Modifier.padding(start = 6.dp, bottom = 8.dp), color = c.text)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
                .border(1.dp, c.divider, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun CardDivider() {
    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(VoidTheme.colors.divider))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = VoidTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionContent(section: SettingsSection, vm: NotesViewModel, s: AppSettings) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    when (section) {
        SettingsSection.LOOK -> {
            SettingsCard("Design") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeMode.entries.forEach { m -> ThemeTile(m, s.theme == m) { vm.editSettings { it.copy(theme = m) } } }
                }
            }
            SettingsCard("Akzentfarbe") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accent.entries.forEach { a ->
                        val color = if (a == Accent.MONO) c.text else Color(a.argb)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .border(2.dp, if (a == s.accent) c.text else Color.Transparent, CircleShape)
                                    .padding(5.dp).clip(CircleShape).background(color)
                                    .clickable { vm.editSettings { it.copy(accent = a) } },
                            )
                            Spacer(Modifier.height(4.dp))
                            Label(a.label, color = if (a == s.accent) c.text else c.textMuted)
                        }
                    }
                }
            }
            SettingsCard("Schrift & Breite") {
                Text("Schriftgröße", color = c.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextSize.entries.forEach { t -> Pill(t.label, s.textSize == t, { vm.editSettings { it.copy(textSize = t) } }) }
                }
                Spacer(Modifier.height(10.dp))
                CardDivider()
                Spacer(Modifier.height(10.dp))
                Text("Seitenbreite (DeX & Tablet)", color = c.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageWidth.entries.forEach { w -> Pill(w.label, s.pageWidth == w, { vm.editSettings { it.copy(pageWidth = w) } }) }
                }
            }
            SettingsCard("Nothing-Stil") {
                Toggle("Dot-Matrix-Überschriften", "Pixelschrift für Überschriften", s.dotHeadings) { v -> vm.editSettings { it.copy(dotHeadings = v) } }
                CardDivider()
                Toggle("Punkteraster", "Punktmuster im Hintergrund", s.dotGrid) { v -> vm.editSettings { it.copy(dotGrid = v) } }
            }
        }
        SettingsSection.EDITOR -> {
            SettingsCard("Neue Seite") {
                Hint("Was passiert beim Stift / Strg+N?")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewPageMode.entries.forEach { m -> Pill(m.label, s.newPage == m, { vm.editSettings { it.copy(newPage = m) } }) }
                }
            }
            SettingsCard("Editor") {
                Toggle("Autokorrektur", "Rechtschreibvorschläge der Tastatur", s.spellCheck) { v -> vm.editSettings { it.copy(spellCheck = v) } }
            }
            SettingsCard("Startseite & Seitenleiste") {
                Toggle("Zuletzt verwendet", "Kürzlich geöffnete Seiten auf der Startseite", s.showRecent) { v -> vm.editSettings { it.copy(showRecent = v) } }
                CardDivider()
                Toggle("Seitenleiste", "Seitenbaum links auf großen Bildschirmen (Strg+\\)", s.sidebar) { v -> vm.editSettings { it.copy(sidebar = v) } }
            }
        }
        SettingsSection.BACKUP -> BackupSection(vm, s)
        SettingsSection.SHARE -> ShareSection(vm, s)
        SettingsSection.IMPORT -> {
            SettingsCard("Notion") { NotionSection(vm) }
            SettingsCard("Markdown") {
                Hint("Einzelne .md-Dateien aus anderen Programmen (z. B. Obsidian). Jede Datei wird eine Seite – ohne Unterseiten und Anhänge.")
                Spacer(Modifier.height(10.dp))
                Pill("Markdown-Dateien wählen", selected = false, onClick = { actions.importMarkdown() })
            }
        }
        SettingsSection.PDF -> SettingsCard("PDF-Export & Drucken") {
            Toggle("Symbol im Titel", "Emoji der Seite vor dem Titel drucken", s.pdfIncludeIcon) { v -> vm.editSettings { it.copy(pdfIncludeIcon = v) } }
            CardDivider()
            Toggle("Fußzeile", "Seitentitel und Seitenzahl auf jeder Seite", s.pdfPageNumbers) { v -> vm.editSettings { it.copy(pdfPageNumbers = v) } }
        }
        SettingsSection.UPDATES -> UpdateSection(vm, s)
        SettingsSection.ABOUT -> SettingsCard("VOID Notes") {
            Text("Version ${BuildConfig.VERSION_NAME}", color = c.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Hint("Notizen im Nothing-Stil. Alles bleibt auf dem Gerät, geteilte Seiten sind Ende-zu-Ende verschlüsselt.")
            Spacer(Modifier.height(8.dp))
            Hint("Schriften: Doto & Space Mono (SIL Open Font License).")
        }
    }
}

@Composable
private fun UpdateSection(vm: NotesViewModel, s: AppSettings) {
    val c = VoidTheme.colors
    LaunchedEffect(Unit) { if (vm.releases.isEmpty()) vm.checkForUpdates(manual = false) }
    val update = vm.availableUpdate
    SettingsCard("Version") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Installiert: ${BuildConfig.VERSION_NAME}", color = c.text, style = MaterialTheme.typography.bodyLarge)
            vm.updateStatus?.let {
                Text(it, color = if (update != null) c.accent else c.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (vm.checkingUpdate) CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
    }
    Spacer(Modifier.height(12.dp))
    val dl = vm.download
    if (dl != null) {
        val f = if (dl.total > 0) (dl.done.toFloat() / dl.total).coerceIn(0f, 1f) else 0f
        DotBar(f, Modifier.fillMaxWidth().height(10.dp))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("${formatSize(dl.done)} / ${formatSize(dl.total)}", Modifier.weight(1f))
            TextButton(onClick = { vm.cancelDownload() }) { Text("ABBRECHEN", style = MaterialTheme.typography.labelLarge, color = c.accent) }
        }
    } else {
        FlowRow2 {
            if (update != null) Pill("Auf ${update.version} aktualisieren", selected = true, onClick = { vm.downloadUpdate(update) })
            Pill("Nach Updates suchen", selected = update == null, onClick = { vm.checkForUpdates(manual = true) })
        }
    }
    Spacer(Modifier.height(8.dp))
    CardDivider()
    Toggle("Beim Start prüfen", "Beim Öffnen der App nach neuen Versionen suchen", s.autoUpdateCheck) { v -> vm.editSettings { it.copy(autoUpdateCheck = v) } }
    }

    SettingsCard("Changelog") {
    if (vm.releases.isEmpty()) {
        Text(
            if (vm.checkingUpdate) "Wird geladen …" else "Changelog nicht verfügbar – Internetverbindung prüfen.",
            color = c.textMuted, style = MaterialTheme.typography.bodyMedium,
        )
    }
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) vm.releases else vm.releases.take(5)
    shown.forEach { r ->
        val installed = r.version == BuildConfig.VERSION_NAME
        val newer = Updater.isNewer(r.version, BuildConfig.VERSION_NAME)
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.version, style = headingStyle(22), color = c.text)
                Spacer(Modifier.width(10.dp))
                Label(r.publishedAt.take(10).split('-').reversed().joinToString("."), Modifier.weight(1f))
                when {
                    installed -> Label("Installiert", color = c.text)
                    newer -> Label("Neu", color = c.accent)
                }
            }
            if (r.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(r.notes, color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (vm.releases.size > 5) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "WENIGER" else "ALLE ${vm.releases.size} VERSIONEN", style = MaterialTheme.typography.labelLarge, color = c.accent)
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(28.dp))
    Label(title, color = VoidTheme.colors.accent)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ThemeTile(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val c = VoidTheme.colors
    val swatch = themeSwatches[mode]
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.divider, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (swatch != null) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(swatch).border(1.dp, c.divider, RoundedCornerShape(12.dp)))
            } else {
                Row(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) {
                    Box(Modifier.weight(1f).height(40.dp).background(Color.Black))
                    Box(Modifier.weight(1f).height(40.dp).background(Color(0xFFF7F6F3)))
                }
            }
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
        }
        Spacer(Modifier.height(4.dp))
        Label(mode.label, color = if (selected) c.text else c.textMuted)
    }
}

@Composable
private fun Toggle(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = VoidTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
                uncheckedThumbColor = c.textMuted, uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.divider,
            ),
        )
    }
}

/** Segmented dot bar (download progress). */
@Composable
fun DotBar(fraction: Float, modifier: Modifier = Modifier, dots: Int = 32) {
    val c = VoidTheme.colors
    androidx.compose.foundation.Canvas(modifier) {
        val gap = size.width / dots
        val r = (gap * 0.32f).coerceAtMost(size.height / 2)
        val filled = (fraction.coerceIn(0f, 1f) * dots).toInt()
        for (i in 0 until dots) {
            drawCircle(
                if (i < filled) c.accent else c.textMuted.copy(alpha = 0.28f),
                r, androidx.compose.ui.geometry.Offset(gap * i + gap / 2, size.height / 2),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupSection(vm: NotesViewModel, s: AppSettings) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsCard("Automatische Sicherung in Ordner") {
        Hint(
            "Komplette Sicherung mit allen Anhängen als ZIP – automatisch beim Verlassen der App (höchstens alle 10 Minuten, " +
                "nur nach Änderungen). Die neuesten ${app.voidnotes.data.AutoBackup.KEEP} bleiben erhalten. " +
                "Tipp: einen Ordner in Google Drive wählen (in der Ordnerauswahl links „Drive“).",
        )
        Spacer(Modifier.height(12.dp))
        if (s.autoBackupUri.isNotBlank()) {
            val name = remember(s.autoBackupUri) { app.voidnotes.data.AutoBackup.folderName(context, s.autoBackupUri) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34C759)))
                Spacer(Modifier.width(8.dp))
                Text("Aktiv · Ordner „$name“", color = c.text, style = MaterialTheme.typography.bodyLarge)
            }
            Label(if (s.autoBackupLast > 0) "Letzte Sicherung: ${formatDate(s.autoBackupLast)}" else "Noch keine Sicherung", Modifier.padding(start = 16.dp))
            Spacer(Modifier.height(12.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.autoBackupUri.isBlank()) {
                Pill("Ordner wählen", selected = true, onClick = { actions.pickBackupFolder() })
            } else {
                Pill(if (vm.backupRunning) "Sichert …" else "Jetzt sichern", selected = true, onClick = { vm.autoBackup(force = true) })
                Pill("Anderer Ordner", selected = false, onClick = { actions.pickBackupFolder() })
                Pill("Ausschalten", selected = false, onClick = { vm.clearBackupFolder() })
            }
        }
    }
    SettingsCard("Google-Sicherung") {
        Hint(
            "Seiten, Vorlagen und Einstellungen sichert Android automatisch in deinem Google-Konto (etwa einmal täglich beim " +
                "Laden im WLAN) und stellt sie nach einer Neuinstallation wieder her. Voraussetzung: Android-Einstellungen → Google → " +
                "Sicherung ist an. Anhänge sind wegen der 25-MB-Grenze nicht dabei – dafür die Ordner-Sicherung oben nutzen.",
        )
    }
    SettingsCard("Manuell") {
        Hint("Eine Sicherung als ZIP speichern oder eine Sicherung zurückspielen (gleichnamige Seiten werden ersetzt).")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Sicherung erstellen", selected = false, onClick = { actions.backup() })
            Pill("Wiederherstellen", selected = false, onClick = { actions.restore() })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareSection(vm: NotesViewModel, s: AppSettings) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    var url by remember { mutableStateOf(s.syncUrl) }
    var guide by remember { mutableStateOf(false) }
    val active = vm.syncHost()
    SettingsCard("Freigabe per Link") {
    Text(
        "Seiten lassen sich über ⋮ → „Per Link teilen“ freigeben. Alle mit dem Link bearbeiten live mit; der Inhalt ist " +
            "Ende-zu-Ende verschlüsselt. Für den Abgleich braucht es eine eigene, kostenlose Firebase-Datenbank – " +
            "wer einen Link öffnet, braucht nichts einzurichten.",
        color = c.textMuted, style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(10.dp))
    Label(if (active.isBlank()) "Noch keine Datenbank eingerichtet" else "Aktiv: $active", color = if (active.isBlank()) c.accent else c.text)
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.OutlinedTextField(
        url, { url = it }, singleLine = true,
        label = { Text("Firebase-Datenbank-URL") },
        placeholder = { Text("https://…-default-rtdb.europe-west1.firebasedatabase.app") },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.divider, cursorColor = c.accent,
            focusedTextColor = c.text, unfocusedTextColor = c.text, focusedLabelColor = c.accent, unfocusedLabelColor = c.textMuted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Speichern", selected = url.trim() != s.syncUrl, onClick = {
            vm.editSettings { it.copy(syncUrl = url.trim()) }
            vm.message(if (url.isBlank()) "Datenbank entfernt" else "Datenbank gespeichert")
        })
        Pill("Geteilten Link öffnen", selected = false, onClick = { vm.dialog = Dialog.OpenLink })
        Pill(if (guide) "Anleitung ausblenden" else "Anleitung", selected = false, onClick = { guide = !guide })
    }
    Spacer(Modifier.height(12.dp))
    LinkOpeningRow()
    if (guide) {
        Spacer(Modifier.height(12.dp))
        Text(
            "1. console.firebase.google.com öffnen → „Projekt hinzufügen“ (Google Analytics kann aus bleiben).\n" +
                "2. Links „Realtime Database“ → „Datenbank erstellen“ → Standort z. B. Belgien (europe-west1) → „Im gesperrten Modus starten“.\n" +
                "3. Reiter „Regeln“: den Inhalt durch die Regeln unten ersetzen → „Veröffentlichen“.\n" +
                "4. Die Adresse oben auf der Datenseite (…firebasedatabase.app) kopieren und hier eintragen.\n" +
                "5. Für die Webseite hinter den Links: im GitHub-Repo Settings → Pages → Source „Deploy from a branch“ → " +
                "Branch ccr-cb163825-s6okw8, Ordner /docs → Save.\n\n" +
                "Optional für alle künftigen Builds: im GitHub-Repo unter Settings → Secrets and variables → Actions → Variables " +
                "die Variable VOID_FIREBASE_URL mit dieser Adresse anlegen.",
            color = c.text, style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            app.voidnotes.data.FIREBASE_RULES, color = c.textMuted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh).padding(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Pill("Regeln kopieren", selected = false, onClick = { actions.launcher.copy("Firebase-Regeln", app.voidnotes.data.FIREBASE_RULES); vm.message("Regeln kopiert") })
    }
    }
}

@Composable
private fun NotionSection(vm: NotesViewModel) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    Text("Aus Notion übernehmen", color = c.text, style = MaterialTheme.typography.bodyLarge)
    Text(
        "In Notion: Einstellungen → Allgemein (bzw. Workspace) → „Alle Workspace-Inhalte exportieren“ → Format „Markdown & CSV“, " +
            "„Unterseiten einschließen“ und „Ordner für Unterseiten erstellen“ an. Die heruntergeladene ZIP hier auswählen. " +
            "Übernommen werden Seiten mit Unterseiten, Bilder und Dateien, Checkboxen, Hinweise, aufklappbare Abschnitte und " +
            "Datenbanken (als Seite mit ihren Einträgen).",
        color = c.textMuted, style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(10.dp))
    val status = vm.importStatus
    if (status != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Label(status, color = c.text)
        }
    } else {
        Pill("Notion-Export (ZIP) importieren", selected = true, onClick = { actions.importNotion() })
    }
}
