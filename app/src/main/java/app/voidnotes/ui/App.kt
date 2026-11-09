package app.voidnotes.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.voidnotes.CrashReporter
import app.voidnotes.data.AppSettings
import app.voidnotes.data.Page
import app.voidnotes.data.Template
import app.voidnotes.ui.editor.EditTarget
import app.voidnotes.ui.editor.EditorScreen
import app.voidnotes.ui.theme.VoidTheme
import java.io.File

val LocalActions = staticCompositionLocalOf<AppActions> { error("AppActions fehlt") }

/** Actions that need an activity: pickers, exports, sharing, printing. */
class AppActions(
    private val vm: NotesViewModel,
    val launcher: Launcher,
    private val pickFiles: () -> Unit,
    private val pickImages: () -> Unit,
    private val camera: (File) -> Unit,
    private val createPdf: (String) -> Unit,
    private val createMarkdown: (String) -> Unit,
    private val createBackup: () -> Unit,
    private val openBackup: () -> Unit,
    private val openMarkdown: () -> Unit,
    private val pickFolder: () -> Unit,
) {
    fun pickBackupFolder() = pickFolder()
    fun copy(label: String, text: String) {
        launcher.copy(label, text)
        vm.message("Link kopiert")
    }
    fun attachFiles() = pickFiles()
    fun attachImages() = pickImages()
    fun takePhoto() = camera(vm.newPhotoTarget())
    fun exportPdf(id: String) = createPdf(id)
    fun exportMarkdown(id: String) = createMarkdown(id)
    fun backup() = createBackup()
    fun restore() = openBackup()
    fun importMarkdown() = openMarkdown()

    fun print(id: String) = vm.prepare(id, "pdf") { launcher.print(it.file, vm.store.page(id)?.displayTitle ?: "Notiz", it.pages) }
    fun sharePdf(id: String) = vm.prepare(id, "pdf") { launcher.share(it.file, it.mime, vm.store.page(id)?.displayTitle.orEmpty()) }
    fun shareMarkdown(id: String) = vm.prepare(id, "md") { launcher.share(it.file, "text/markdown", vm.store.page(id)?.displayTitle.orEmpty()) }
    fun shareText(id: String) = vm.prepare(id, "txt") { launcher.shareText(vm.store.page(id)?.displayTitle.orEmpty(), it.file.readText()) }

    fun openAttachment(file: String, mime: String, name: String) = launcher.open(vm.store.attachment(file), mime, name)
    fun shareAttachment(file: String, mime: String, name: String) = launcher.share(vm.store.attachment(file), mime.ifBlank { "*/*" }, name)
    fun openUrl(url: String) = launcher.openUrl(url)
}

@Composable
fun VoidNotesRoot(vm: NotesViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pages by vm.store.pages.collectAsStateWithLifecycle()
    val templates by vm.store.templates.collectAsStateWithLifecycle()
    val c = VoidTheme.colors
    val context = LocalContext.current
    val launcher = remember { Launcher(context) }
    val snackbar = remember { SnackbarHostState() }

    // ---- activity result launchers
    val pendingExport = remember { arrayOfNulls<String>(1) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.attach(it) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.attach(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { vm.photoTaken(it) }
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val id = pendingExport[0]
        if (uri != null && id != null) vm.exportTo(id, "pdf", uri)
    }
    val createMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val id = pendingExport[0]
        if (uri != null && id != null) vm.exportTo(id, "md", uri)
    }
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::backupTo) }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restoreFrom) }
    val openMd = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importMarkdown(it) }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.setBackupFolder(it) }

    val actions = remember(vm) {
        AppActions(
            vm, launcher,
            pickFiles = { runCatching { pickFiles.launch(arrayOf("*/*")) } },
            pickImages = { runCatching { pickImages.launch(arrayOf("image/*")) } },
            camera = { file -> runCatching { camera.launch(launcher.uriFor(file)) }.onFailure { vm.message("Kamera nicht verfügbar") } },
            createPdf = { id -> pendingExport[0] = id; runCatching { createPdf.launch(vm.fileName(id, "pdf")) } },
            createMarkdown = { id -> pendingExport[0] = id; runCatching { createMd.launch(vm.fileName(id, "md")) } },
            createBackup = {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMAN).format(java.util.Date())
                runCatching { createZip.launch("VOID-Notes-Sicherung-$date.zip") }
            },
            openBackup = { runCatching { openZip.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) } },
            openMarkdown = { runCatching { openMd.launch(arrayOf("text/markdown", "text/plain", "text/x-markdown", "application/octet-stream")) } },
            pickFolder = { runCatching { openTree.launch(null) } },
        )
    }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Message -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(e.text)
                }
                is UiEvent.Install -> launcher.installApk(e.file)
            }
        }
    }

    // ---- global keyboard shortcuts (Samsung DeX)
    val focusManager = LocalFocusManager.current
    DisposableEffect(vm) {
        vm.keyHandler = handler@{ e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@handler false
            val ctrl = e.isCtrlPressed
            val shift = e.isShiftPressed
            val pageId = (vm.screen as? Screen.PageScreen)?.id
            when {
                ctrl && !shift && e.keyCode == KeyEvent.KEYCODE_N -> { vm.requestNewPage(null); true }
                ctrl && shift && e.keyCode == KeyEvent.KEYCODE_N -> { pageId?.let { vm.newSubPage(it) }; pageId != null }
                ctrl && (e.keyCode == KeyEvent.KEYCODE_K || e.keyCode == KeyEvent.KEYCODE_F) -> { vm.navigate(Screen.Search); true }
                ctrl && e.keyCode == KeyEvent.KEYCODE_P -> { pageId?.let { actions.print(it) }; pageId != null }
                ctrl && shift && e.keyCode == KeyEvent.KEYCODE_E -> { pageId?.let { actions.exportPdf(it) }; pageId != null }
                ctrl && e.keyCode == KeyEvent.KEYCODE_BACKSLASH -> { vm.editSettings { it.copy(sidebar = !it.sidebar) }; true }
                ctrl && e.keyCode == KeyEvent.KEYCODE_COMMA -> { vm.navigate(Screen.Settings); true }
                e.keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                    val ed = vm.editor
                    when {
                        ed?.slashFor != null -> false
                        ed?.focusedId != null -> { focusManager.clearFocus(); true }
                        else -> vm.back()
                    }
                }
                else -> false
            }
        }
        onDispose { vm.keyHandler = null }
    }

    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    androidx.compose.runtime.CompositionLocalProvider(LocalActions provides actions) {
        BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
            val wide = maxWidth >= 840.dp
            val sidebarWidth = if (maxWidth > 1300.dp) 320.dp else 280.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    if (settings.sidebar) {
                        Sidebar(vm, pages, settings, Modifier.width(sidebarWidth).fillMaxHeight())
                        Box(Modifier.width(1.dp).fillMaxHeight().background(c.divider))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { Content(vm, pages, templates, settings, wide = true) }
                }
            } else {
                Content(vm, pages, templates, settings, wide = false)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 80.dp)) { data ->
                Snackbar(
                    containerColor = c.text, contentColor = c.background, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(data.visuals.message) }
            }
        }
        Dialogs(vm, pages, templates)
        StartupDialogs(vm)
    }
}

@Composable
private fun Content(vm: NotesViewModel, pages: Map<String, Page>, templates: List<Template>, settings: AppSettings, wide: Boolean) {
    val editor = vm.editor
    when (val s = vm.screen) {
        Screen.Home -> if (wide) Dashboard(vm, pages, templates) else HomeScreen(vm, pages, settings)
        is Screen.PageScreen, is Screen.TemplateScreen -> {
            if (editor != null) {
                androidx.compose.runtime.key(editor) { EditorScreen(vm, editor, pages, templates, settings, wide) }
            }
        }
        Screen.Search -> SearchScreen(vm, pages, wide)
        Screen.Templates -> TemplatesScreen(vm, templates, wide)
        Screen.Trash -> TrashScreen(vm, pages, wide)
        Screen.Settings -> SettingsScreen(vm, settings, wide)
    }
}

@Composable
private fun StartupDialogs(vm: NotesViewModel) {
    val context = LocalContext.current
    val launcher = LocalActions.current.launcher
    val crash = remember { androidx.compose.runtime.mutableStateOf(CrashReporter.pending(context)) }
    crash.value?.let { report ->
        CrashDialog(
            report,
            onShare = {
                launcher.shareText("VOID Notes Absturzbericht", report)
                CrashReporter.clear(context); crash.value = null
            },
            onDismiss = { CrashReporter.clear(context); crash.value = null },
        )
    }
    val update = vm.availableUpdate
    if (vm.showUpdateDialog && update != null) {
        UpdateDialog(update, onDismiss = { vm.showUpdateDialog = false }) { vm.downloadUpdate(update) }
    }
}

@Composable
private fun Dialogs(vm: NotesViewModel, pages: Map<String, Page>, templates: List<Template>) {
    val dismiss = { vm.dialog = null }
    vm.newPageParent?.let { parent ->
        TemplatePickerDialog(
            templates, onDismiss = { vm.newPageParent = null },
            onOpenLink = if (parent.isEmpty()) ({ vm.newPageParent = null; vm.dialog = Dialog.OpenLink }) else null,
        ) { t -> vm.newPage(parent.ifEmpty { null }, t) }
    }
    when (val d = vm.dialog) {
        null -> {}
        is Dialog.Rename -> {
            val p = pages[d.id] ?: return dismiss()
            TextInputDialog("Umbenennen", p.title, "Speichern", onDismiss = dismiss) { vm.rename(d.id, it); dismiss() }
        }
        is Dialog.Icon -> {
            val p = pages[d.id] ?: return dismiss()
            val current = (vm.editor?.takeIf { it.target == EditTarget.PageTarget(d.id) }?.icon) ?: p.icon
            EmojiDialog(current, onDismiss = dismiss) { vm.setIcon(d.id, it); dismiss() }
        }
        Dialog.EditorIcon -> EmojiDialog(vm.editor?.icon.orEmpty(), onDismiss = dismiss) { vm.setTemplateIcon(it); dismiss() }
        is Dialog.Move -> MovePageDialog(vm, d.id, pages, onDismiss = dismiss)
        is Dialog.SaveTemplate -> {
            val p = pages[d.id] ?: return dismiss()
            TemplateInfoDialog("Als Vorlage", p.title.ifBlank { "Neue Vorlage" }, "", "Speichern", onDismiss = dismiss) { name, desc ->
                vm.saveAsTemplate(d.id, name, desc); dismiss()
            }
        }
        is Dialog.TemplateInfo -> {
            val t = templates.firstOrNull { it.id == d.id } ?: return dismiss()
            TemplateInfoDialog("Vorlage", t.name, t.description, "Speichern", onDismiss = dismiss) { name, desc ->
                vm.updateTemplateInfo(d.id, name, desc); dismiss()
            }
        }
        is Dialog.DeleteTemplate -> {
            val t = templates.firstOrNull { it.id == d.id } ?: return dismiss()
            ConfirmDialog("Löschen", "Vorlage „${t.name}“ löschen? Seiten, die damit erstellt wurden, bleiben erhalten.", "Löschen", dismiss) {
                vm.deleteTemplate(d.id); dismiss()
            }
        }
        is Dialog.DeleteForever -> {
            val p = pages[d.id] ?: return dismiss()
            ConfirmDialog("Endgültig löschen", "„${p.displayTitle}“ und alle Unterseiten und Anhänge endgültig löschen?", "Löschen", dismiss) {
                vm.deleteForever(d.id); dismiss()
            }
        }
        Dialog.EmptyTrash -> ConfirmDialog("Papierkorb leeren", "Alle Seiten im Papierkorb endgültig löschen?", "Leeren", dismiss) {
            vm.emptyTrash(); dismiss()
        }
        is Dialog.CalloutEmoji -> {
            val e = vm.editor
            val b = e?.block(d.blockId) ?: return dismiss()
            EmojiDialog(b.emoji ?: "💡", allowRemove = false, onDismiss = dismiss) { emoji ->
                e.checkpoint(); b.emoji = emoji.ifBlank { "💡" }; e.changed(); dismiss()
            }
        }
        is Dialog.Share -> {
            val p = pages[d.id] ?: return dismiss()
            ShareDialog(vm, p, dismiss)
        }
        is Dialog.StopShare -> {
            val p = pages[d.id] ?: return dismiss()
            StopShareDialog(vm, p, dismiss)
        }
        Dialog.OpenLink -> OpenLinkDialog(vm, dismiss)
        is Dialog.RenameFile -> {
            val b = vm.editor?.block(d.blockId) ?: return dismiss()
            TextInputDialog("Umbenennen", b.fileName.orEmpty(), "Speichern", onDismiss = dismiss) { vm.renameAttachment(d.blockId, it); dismiss() }
        }
    }
}
