package app.voidnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voidnotes.BuildConfig
import app.voidnotes.data.Page
import app.voidnotes.data.Release
import app.voidnotes.data.Template
import app.voidnotes.ui.theme.VoidTheme

@Composable
private fun fieldColors() = VoidTheme.colors.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.divider, cursorColor = c.accent,
        focusedTextColor = c.text, unfocusedTextColor = c.text, focusedLabelColor = c.accent, unfocusedLabelColor = c.textMuted,
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, confirmText: String, label: String? = null, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    VoidDialog(title, onDismiss, confirmText, { onConfirm(value.text.trim()) }) {
        OutlinedTextField(
            value = value, onValueChange = { value = it }, singleLine = true,
            label = label?.let { { Text(it) } },
            colors = fieldColors(), modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    VoidDialog(title, onDismiss, confirm, onConfirm) {
        Text(text, color = VoidTheme.colors.text, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Name and description of a template (save page as template, edit template info). */
@Composable
fun TemplateInfoDialog(title: String, name: String, description: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var n by remember { mutableStateOf(TextFieldValue(name, TextRange(0, name.length))) }
    var d by remember { mutableStateOf(description) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    VoidDialog(title, onDismiss, confirm, { onConfirm(n.text.trim(), d.trim()) }, confirmEnabled = n.text.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(n, { n = it }, singleLine = true, label = { Text("Name") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth().focusRequester(focus))
            OutlinedTextField(d, { d = it }, singleLine = true, label = { Text("Beschreibung (optional)") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            Text(
                "Platzhalter wie {{datum}} im Titel oder Text werden beim Anlegen einer Seite ersetzt.",
                color = VoidTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val emojiGroups = listOf(
    "Häufig" to "📝 📄 📌 ✅ ☑️ 📋 🗓️ 📅 ⏰ 💡 ⭐ 🔥 ❤️ 🔒 🔑 📎 📁 🗂️ 📚 📖 📓 📒 ✏️ 🖊️",
    "Arbeit" to "💼 🏢 📊 📈 📉 🧾 💰 💶 🧮 🖥️ 💻 ⌨️ 🖨️ 📞 ✉️ 📬 🤝 🎯 🚀 🧭 🛠️ ⚙️ 🧪 🔬",
    "Reise" to "✈️ 🚁 🛫 🛬 🚗 🚆 🚢 🗺️ 🏔️ 🏖️ 🏕️ 🌍 🧳 ⛽ 🅿️ 🏨 🎫 📍 🧗 🚴 🏃 ⛑️ 🪂 🌦️",
    "Leben" to "🏠 🛒 🍳 🍽️ ☕ 🍎 🥗 🏋️ 🧘 💊 🩺 👶 🐶 🐱 🌱 🌸 🎁 🎉 🎂 🎵 🎬 🎮 📷 🎨",
    "Symbole" to "⚠️ ❗ ❓ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🔺 🔻 ➡️ ⬅️ 🔁 ♻️ ✳️ ❌ ⭕ 💬 👋 🤖 🧠",
)

/** Emoji picker for page icons and callouts; any emoji can also be typed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiDialog(current: String, onDismiss: () -> Unit, allowRemove: Boolean = true, onPick: (String) -> Unit) {
    val c = VoidTheme.colors
    var custom by remember { mutableStateOf("") }
    VoidDialog(
        "Symbol", onDismiss,
        confirmText = if (allowRemove && current.isNotBlank()) "Entfernen" else null,
        onConfirm = { onPick("") },
    ) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    custom, { custom = it.take(8) }, singleLine = true, label = { Text("Eigenes Emoji") },
                    colors = fieldColors(), modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Pill("OK", selected = custom.isNotBlank(), onClick = { if (custom.isNotBlank()) onPick(custom.trim()) })
            }
            emojiGroups.forEach { (name, list) ->
                Label(name)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    list.split(' ').filter { it.isNotBlank() }.forEach { e ->
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (e == current) c.selection else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onPick(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }
        }
    }
}

/** Choose how a new page starts: empty or from a template. */
@Composable
fun TemplatePickerDialog(templates: List<Template>, onDismiss: () -> Unit, onOpenLink: (() -> Unit)? = null, onNotion: (() -> Unit)? = null, onPick: (Template?) -> Unit) {
    VoidDialog("Neue Seite", onDismiss, null, {}) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            TemplateRow(icon = "📄", name = "Leere Seite", description = "Ohne Vorlage beginnen") { onPick(null) }
            onOpenLink?.let { open -> TemplateRow(icon = "🔗", name = "Geteilten Link öffnen", description = "Notiz, die jemand mit dir geteilt hat") { open() } }
            onNotion?.let { open -> TemplateRow(icon = "📥", name = "Aus Notion importieren", description = "Export-ZIP („Markdown & CSV“) einlesen") { open() } }
            templates.forEach { t -> TemplateRow(t.icon, t.name, t.description) { onPick(t) } }
        }
    }
}

@Composable
fun TemplateRow(icon: String, name: String, description: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    val c = VoidTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Text(icon.ifBlank { "📄" }, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description.isNotBlank()) Text(description, color = c.textMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Pick a new parent for a page. */
@Composable
fun MovePageDialog(vm: NotesViewModel, pageId: String, pages: Map<String, Page>, onDismiss: () -> Unit) {
    val c = VoidTheme.colors
    var filter by remember { mutableStateOf("") }
    val candidates = remember(pages, filter) {
        pages.values.filter { !it.inTrash && vm.canMoveTo(pageId, it.id) && it.id != pages[pageId]?.parentId }
            .filter { filter.isBlank() || it.displayTitle.contains(filter, ignoreCase = true) }
            .sortedBy { it.displayTitle.lowercase() }
    }
    VoidDialog("Verschieben", onDismiss, null, {}) {
        Column {
            BasicTextField(
                filter, { filter = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh).padding(12.dp),
                decorationBox = { inner -> Box { if (filter.isEmpty()) Text("Seite suchen …", color = c.textMuted); inner() } },
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (pages[pageId]?.parentId != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.move(pageId, null); onDismiss() }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Home, null, Modifier.size(22.dp), tint = c.textMuted)
                        Spacer(Modifier.width(10.dp))
                        Text("Oberste Ebene (Privat)", color = c.text)
                    }
                }
                candidates.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.move(pageId, p.id); onDismiss() }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PageIcon(p, 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(p.displayTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(release: Release, onDismiss: () -> Unit, onUpdate: () -> Unit) {
    val c = VoidTheme.colors
    VoidDialog("Update", onDismiss, "Aktualisieren", onUpdate, dismissText = "Später") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Version ${release.version} ist verfügbar (installiert: ${BuildConfig.VERSION_NAME}).", color = c.text)
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Label("Neu in dieser Version")
                Spacer(Modifier.height(6.dp))
                Text(release.notes, color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
            }
            if (release.apkSize > 0) {
                Spacer(Modifier.height(10.dp))
                Label("Download: ${formatSize(release.apkSize)}")
            }
        }
    }
}

@Composable
fun CrashDialog(report: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    val c = VoidTheme.colors
    VoidDialog("Absturz", onDismiss, "Bericht teilen", onShare, dismissText = "Schließen") {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "VOID Notes ist beim letzten Mal abgestürzt. Teile den Bericht, damit der Fehler behoben werden kann. " +
                    "Er enthält nur Gerät, Android-Version und die Fehlerstelle – keine Notizen.",
                color = c.text,
            )
            Text(report.lines().take(14).joinToString("\n"), color = c.textMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Small round "+" used in rows of the tree. */
@Composable
fun AddDot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = VoidTheme.colors
    Box(
        modifier.size(34.dp).clip(RoundedCornerShape(50)).background(c.accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Add, "Neu", Modifier.size(18.dp), tint = c.onAccent) }
}

/** Link of a shared page with copy / share buttons and the live status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareDialog(vm: NotesViewModel, page: Page, onDismiss: () -> Unit) {
    val c = VoidTheme.colors
    val actions = LocalActions.current
    val info = page.share ?: return onDismiss()
    val session = vm.sync?.takeIf { it.pageId == page.id }
    VoidDialog("Teilen", onDismiss, "Link teilen", { actions.launcher.shareText("VOID Notes: ${page.displayTitle}", info.link) }, dismissText = "Schließen") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Jeder mit diesem Link kann die Seite live mitlesen und mitbearbeiten – in VOID Notes oder direkt im Browser, ohne App. Der Inhalt ist Ende-zu-Ende verschlüsselt – " +
                    "der Schlüssel steckt nur im Link. Teile ihn also nur mit Personen, denen du vertraust.",
                color = c.text, style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                info.link, color = c.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh).padding(12.dp),
            )
            if (session != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SyncDot(session.state)
                    Spacer(Modifier.width(8.dp))
                    Label(session.error ?: session.state.label)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Kopieren", selected = false, onClick = { actions.copy("VOID Notes Link", info.link) })
                Pill("Freigabe beenden", selected = false, onClick = { vm.dialog = Dialog.StopShare(page.id) })
            }
            LinkOpeningRow()
            Text(
                "Anhänge bis 3 MB werden mit geteilt. Unterseiten bleiben privat.",
                color = c.textMuted, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun StopShareDialog(vm: NotesViewModel, page: Page, onDismiss: () -> Unit) {
    val c = VoidTheme.colors
    VoidDialog("Freigabe beenden", onDismiss, "Für alle löschen", { vm.stopSharing(page.id, deleteOnline = true); onDismiss() }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "„Für alle löschen“ entfernt die Online-Kopie – der Link funktioniert dann für niemanden mehr. " +
                    "„Nur hier“ trennt nur dieses Gerät; die anderen arbeiten weiter. Deine Seite bleibt in beiden Fällen erhalten.",
                color = c.text, style = MaterialTheme.typography.bodyMedium,
            )
            Pill("Nur hier trennen", selected = false, onClick = { vm.stopSharing(page.id, deleteOnline = false); onDismiss() })
        }
    }
}

/** Paste a link someone shared. */
@Composable
fun OpenLinkDialog(vm: NotesViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clip = remember {
        runCatching {
            context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()?.takeIf { app.voidnotes.data.ShareInfo.parse(it) != null }.orEmpty()
    }
    var value by remember { mutableStateOf(clip) }
    VoidDialog("Link öffnen", onDismiss, "Öffnen", { if (vm.joinLink(value)) onDismiss() }, confirmEnabled = value.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Füge den Link einer geteilten Notiz ein.", color = VoidTheme.colors.text)
            OutlinedTextField(value, { value = it }, singleLine = true, label = { Text("Link") }, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SyncDot(state: app.voidnotes.data.SyncState, modifier: Modifier = Modifier) {
    val c = VoidTheme.colors
    val color = when (state) {
        app.voidnotes.data.SyncState.ONLINE -> androidx.compose.ui.graphics.Color(0xFF34C759)
        app.voidnotes.data.SyncState.ERROR -> c.accent
        else -> c.textMuted
    }
    Box(modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
}

/** Small round link button (open a shared note). */
@Composable
fun LinkDot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = VoidTheme.colors
    Box(
        modifier.size(34.dp).clip(RoundedCornerShape(50)).border(1.2.dp, c.accent, RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(androidx.compose.material.icons.Icons.Outlined.Link, "Geteilten Link öffnen", Modifier.size(18.dp), tint = c.accent) }
}

/**
 * Status and fix for "links open in the app": on Android 12+ the link domain must be allowed once
 * (Einstellungen → Apps → VOID Notes → Standardmäßig öffnen → Link hinzufügen).
 */
@Composable
fun LinkOpeningRow() {
    val c = VoidTheme.colors
    val launcher = LocalActions.current.launcher
    var approved by remember { mutableStateOf(launcher.linksApproved()) }
    // Re-check when coming back from the system settings.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) approved = launcher.linksApproved()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (approved == true) {
        Label("✓ Links öffnen direkt in VOID Notes", color = c.text)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Damit Links direkt in der App aufgehen, einmal erlauben: „Link hinzufügen“ → $LINK_HOST anhaken. " +
                "Das gilt auch für alle, die einen Link von dir bekommen.",
            color = c.textMuted, style = MaterialTheme.typography.bodyMedium,
        )
        Pill("Links in App öffnen erlauben", selected = true, onClick = { launcher.openLinkSettings() })
    }
}
