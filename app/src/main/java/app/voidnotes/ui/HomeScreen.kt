package app.voidnotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voidnotes.data.AppSettings
import app.voidnotes.data.Page
import app.voidnotes.data.Template
import app.voidnotes.ui.theme.VoidTheme

/** Opens a menu on right click (mouse in Samsung DeX). */
fun Modifier.onRightClick(action: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                e.changes.forEach { it.consume() }
                action()
            }
        }
    }
}

/** Menu of a page (tree row, card, editor ⋮). */
fun pageMenu(vm: NotesViewModel, actions: AppActions, page: Page, inEditor: Boolean): List<MenuItem?> = listOfNotNull(
    MenuItem("Unterseite hinzufügen", Icons.AutoMirrored.Outlined.NoteAdd) {
        if (inEditor) vm.newSubPage(page.id) else vm.requestNewPage(page.id)
    },
    MenuItem(if (page.favorite) "Aus Favoriten entfernen" else "Zu Favoriten", if (page.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline) {
        vm.toggleFavorite(page.id)
    },
    if (!inEditor) MenuItem("Umbenennen", Icons.Outlined.DriveFileRenameOutline) { vm.dialog = Dialog.Rename(page.id) } else null,
    MenuItem("Symbol ändern", Icons.Outlined.EmojiEmotions) { vm.dialog = Dialog.Icon(page.id) },
    MenuItem("Duplizieren", Icons.Outlined.ContentCopy) { vm.duplicate(page.id) },
    MenuItem("Verschieben nach …", Icons.AutoMirrored.Outlined.DriveFileMove) { vm.dialog = Dialog.Move(page.id) },
    if (!inEditor) MenuItem("Nach oben", Icons.Outlined.ArrowUpward) { vm.reorder(page.id, -1) } else null,
    if (!inEditor) MenuItem("Nach unten", Icons.Outlined.ArrowDownward) { vm.reorder(page.id, +1) } else null,
    Separator,
    MenuItem("Als Vorlage speichern", Icons.Outlined.ViewQuilt) { vm.dialog = Dialog.SaveTemplate(page.id) },
    MenuItem("Als PDF exportieren", Icons.Outlined.PictureAsPdf) { actions.exportPdf(page.id) },
    MenuItem("Drucken", Icons.Outlined.Print) { actions.print(page.id) },
    Separator,
    MenuItem("In den Papierkorb", Icons.Outlined.DeleteOutline, danger = true) { vm.trash(page.id) },
)

/** Top row: workspace logo and the tabs. */
@Composable
fun TabBar(vm: NotesViewModel, compact: Boolean, modifier: Modifier = Modifier) {
    val c = VoidTheme.colors
    val s = vm.screen
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(if (compact) 40.dp else 48.dp).clip(CircleShape).background(c.surface).border(1.dp, c.divider, CircleShape)
                .clickable { vm.navigate(Screen.Settings) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
        }
        TabPill(Icons.Outlined.Home, "Start", s == Screen.Home || s is Screen.PageScreen, Modifier.weight(1f), compact) { vm.navigate(Screen.Home) }
        TabPill(Icons.Outlined.Search, "Suche", s == Screen.Search, Modifier.weight(1f), compact) { vm.navigate(Screen.Search) }
        TabPill(Icons.Outlined.ViewQuilt, "Vorlagen", s == Screen.Templates || s is Screen.TemplateScreen, Modifier.weight(1f), compact) { vm.navigate(Screen.Templates) }
        TabPill(Icons.Outlined.Delete, "Papierkorb", s == Screen.Trash, Modifier.weight(1f), compact) { vm.navigate(Screen.Trash) }
    }
}

@Composable
private fun TabPill(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    val c = VoidTheme.colors
    Box(
        modifier.height(if (compact) 40.dp else 48.dp).clip(RoundedCornerShape(50))
            .background(if (selected) c.surfaceHigh else c.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (selected) c.text else c.textMuted)
    }
}

/** Phone home screen, modelled on the Notion start page. */
@Composable
fun HomeScreen(vm: NotesViewModel, pages: Map<String, Page>, settings: AppSettings) {
    val c = VoidTheme.colors
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    TabBar(vm, compact = false)
                    Spacer(Modifier.height(18.dp))
                }
            }
            pageSections(vm, pages, settings, selectedId = null, cards = true)
        }
        BottomBar(vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** Search bubble and new-page button floating at the bottom. */
@Composable
private fun BottomBar(vm: NotesViewModel, modifier: Modifier) {
    val c = VoidTheme.colors
    Row(
        modifier.fillMaxWidth().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = 0.95f), c.background)),
        ).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(c.surface).border(1.dp, c.divider, CircleShape).clickable { vm.navigate(Screen.Search) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Search, "Suchen", tint = c.text) }
        Row(
            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(50)).background(c.surface).border(1.dp, c.divider, RoundedCornerShape(50))
                .clickable { vm.navigate(Screen.Search) }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(12.dp))
            Text("Notizen durchsuchen …", color = c.textMuted, maxLines = 1)
        }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(c.text).clickable { vm.requestNewPage(null) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.EditNote, "Neue Seite", tint = c.background) }
    }
}

/** Recent, favourites and the page tree, shared by the phone home screen and the DeX sidebar. */
fun androidx.compose.foundation.lazy.LazyListScope.pageSections(
    vm: NotesViewModel,
    pages: Map<String, Page>,
    settings: AppSettings,
    selectedId: String?,
    cards: Boolean,
) {
    val live = pages.values.filterNot { it.inTrash }
    val recent = live.filter { it.opened > 0 }.sortedByDescending { it.opened }.take(8)
    val favorites = live.filter { it.favorite }.sortedBy { it.displayTitle.lowercase() }
    val byParent = live.groupBy { it.parentId }.mapValues { (_, v) -> v.sortedBy { it.order } }

    if (settings.showRecent && recent.isNotEmpty()) {
        item(key = "h-recent") {
            SectionHeader("Zuletzt verwendet", "recent" in settings.collapsedSections, { vm.toggleSection("recent") })
        }
        if ("recent" !in settings.collapsedSections) {
            if (cards) {
                item(key = "recent-cards") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(recent, key = { it.id }) { p -> RecentCard(vm, p) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            } else {
                items(recent.take(5), key = { "r-" + it.id }) { p ->
                    TreeRow(vm, p, depth = 0, hasChildren = false, expanded = false, selected = p.id == selectedId, compact = true, showAdd = false)
                }
                item(key = "r-gap") { Spacer(Modifier.height(10.dp)) }
            }
        }
    }

    if (favorites.isNotEmpty()) {
        item(key = "h-fav") {
            SectionHeader("Favoriten", "fav" in settings.collapsedSections, { vm.toggleSection("fav") })
        }
        if ("fav" !in settings.collapsedSections) {
            val seen = HashSet<String>()
            favorites.forEach { p ->
                treeItems(this, vm, p, byParent, settings, depth = 0, selectedId = selectedId, keyPrefix = "f-", compact = !cards, seen = seen)
            }
            item(key = "f-gap") { Spacer(Modifier.height(10.dp)) }
        }
    }

    item(key = "h-private") {
        SectionHeader("Privat", "private" in settings.collapsedSections, { vm.toggleSection("private") }) {
            AddDot({ vm.requestNewPage(null) })
        }
    }
    if ("private" !in settings.collapsedSections) {
        val roots = byParent[null].orEmpty()
        if (roots.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Noch keine Seiten. Tippe auf +, um eine anzulegen.",
                    color = VoidTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp),
                )
            }
        }
        roots.forEach { p -> treeItems(this, vm, p, byParent, settings, depth = 0, selectedId = selectedId, keyPrefix = "p-", compact = !cards) }
    }
}

private fun treeItems(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    vm: NotesViewModel,
    page: Page,
    byParent: Map<String?, List<Page>>,
    settings: AppSettings,
    depth: Int,
    selectedId: String?,
    keyPrefix: String,
    compact: Boolean,
    seen: MutableSet<String> = HashSet(),
) {
    if (!seen.add(page.id)) return
    val kids = byParent[page.id].orEmpty()
    val expanded = page.id in settings.expanded
    scope.item(key = keyPrefix + page.id) {
        TreeRow(vm, page, depth, kids.isNotEmpty(), expanded, page.id == selectedId, compact, showAdd = true)
    }
    if (expanded) kids.forEach { treeItems(scope, vm, it, byParent, settings, depth + 1, selectedId, keyPrefix, compact, seen) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeRow(
    vm: NotesViewModel,
    page: Page,
    depth: Int,
    hasChildren: Boolean,
    expanded: Boolean,
    selected: Boolean,
    compact: Boolean,
    showAdd: Boolean,
) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rowH: Dp = if (compact) 38.dp else 54.dp
    Box {
        Row(
            Modifier.fillMaxWidth().height(rowH).clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        selected -> c.surfaceHigh
                        hovered -> c.surface
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .onRightClick { menu = true }
                .combinedClickable(onClick = { vm.openPage(page.id) }, onLongClick = { menu = true })
                .padding(start = (depth * (if (compact) 14 else 20)).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(if (compact) 26.dp else 36.dp).clip(CircleShape).clickable(enabled = true) {
                    if (hasChildren) vm.toggleExpanded(page.id) else vm.requestNewPage(page.id)
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (expanded && hasChildren) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    if (expanded) "Zuklappen" else "Aufklappen",
                    Modifier.size(if (compact) 18.dp else 24.dp),
                    tint = if (hasChildren) c.textMuted else c.textMuted.copy(alpha = 0.35f),
                )
            }
            Spacer(Modifier.width(if (compact) 2.dp else 6.dp))
            PageIcon(page, if (compact) 20.dp else 28.dp)
            Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
            Text(
                page.displayTitle,
                color = if (page.title.isBlank()) c.textMuted else c.text,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!compact || hovered || selected) {
                Box(Modifier.size(if (compact) 28.dp else 40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MoreHoriz, "Menü", Modifier.size(if (compact) 18.dp else 22.dp), tint = c.textMuted)
                }
                if (showAdd) {
                    Box(Modifier.size(if (compact) 28.dp else 40.dp).clip(CircleShape).clickable { vm.requestNewPage(page.id) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, "Unterseite", Modifier.size(if (compact) 18.dp else 24.dp), tint = c.textMuted)
                    }
                }
            }
        }
        VoidMenu(menu, { menu = false }, pageMenu(vm, actions, page, inEditor = false))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(vm: NotesViewModel, page: Page) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.width(148.dp).height(150.dp).clip(RoundedCornerShape(18.dp))
                .border(1.dp, c.divider, RoundedCornerShape(18.dp))
                .background(c.surface)
                .onRightClick { menu = true }
                .combinedClickable(onClick = { vm.openPage(page.id) }, onLongClick = { menu = true }),
        ) {
            Box(Modifier.fillMaxWidth().height(70.dp).background(c.surfaceHigh)) {
                DotGrid(Modifier.fillMaxSize(), spacing = 12.dp, radius = 0.9.dp)
                PageIcon(page, 32.dp, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 6.dp))
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(page.displayTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            }
        }
        VoidMenu(menu, { menu = false }, pageMenu(vm, actions, page, inEditor = false))
    }
}

/** Left column on wide screens (Samsung DeX, tablets). */
@Composable
fun Sidebar(vm: NotesViewModel, pages: Map<String, Page>, settings: AppSettings, modifier: Modifier = Modifier) {
    val c = VoidTheme.colors
    val selected = (vm.screen as? Screen.PageScreen)?.id
    Column(modifier.background(c.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Headline("VOID", size = 28)
            Spacer(Modifier.width(8.dp))
            Label("Notes", Modifier.weight(1f))
            RoundButton(Icons.Outlined.Settings, "Einstellungen", { vm.navigate(Screen.Settings) }, size = 36.dp)
        }
        TabBar(vm, compact = true, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50))
                .background(c.text).clickable { vm.requestNewPage(null) }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.EditNote, null, Modifier.size(20.dp), tint = c.background)
            Spacer(Modifier.width(10.dp))
            Text("NEUE SEITE", style = MaterialTheme.typography.labelLarge, color = c.background, modifier = Modifier.weight(1f))
            Label("Strg+N", color = c.background.copy(alpha = 0.6f))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            pageSections(vm, pages, settings, selectedId = selected, cards = false)
        }
    }
}

/** Start page in the content area of wide screens. */
@Composable
fun Dashboard(vm: NotesViewModel, pages: Map<String, Page>, templates: List<Template>) {
    val c = VoidTheme.colors
    val live = pages.values.filterNot { it.inTrash }
    val recent = live.sortedByDescending { maxOf(it.updated, it.opened) }.take(12)
    Box(Modifier.fillMaxSize()) {
        DotGrid(Modifier.fillMaxSize(), spacing = 24.dp)
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                    Headline(greeting(), size = 48)
                    Spacer(Modifier.height(6.dp))
                    Label("${live.size} Seiten · ${templates.size} Vorlagen")
                    Spacer(Modifier.height(32.dp))
                    Label("Neu aus Vorlage")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Pill("Leere Seite", selected = true, onClick = { vm.newPage(null, null) }, icon = Icons.Outlined.Add)
                        }
                        items(templates, key = { it.id }) { t ->
                            Pill("${t.icon} ${t.name}".trim(), selected = false, onClick = { vm.newPage(null, t) })
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Label("Zuletzt bearbeitet")
                    Spacer(Modifier.height(10.dp))
                }
            }
            items(recent, key = { it.id }) { p ->
                Row(
                    Modifier.widthIn(max = 900.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.background.copy(alpha = 0.85f))
                        .clickable { vm.openPage(p.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageIcon(p, 26.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        val path = vm.ancestors(p.id).joinToString(" / ") { it.displayTitle }
                        if (path.isNotEmpty()) Text(path, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                    Label(relativeTime(p.updated))
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Hallo"
        in 18..22 -> "Guten Abend"
        else -> "Gute Nacht"
    }
}
