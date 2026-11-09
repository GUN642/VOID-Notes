package app.voidnotes.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.voidnotes.BuildConfig
import app.voidnotes.data.AppSettings
import app.voidnotes.data.AutoBackup
import app.voidnotes.data.Crypto
import app.voidnotes.data.ShareInfo
import app.voidnotes.data.SyncSession
import app.voidnotes.data.normalizeHost
import app.voidnotes.data.Block
import app.voidnotes.data.BlockType
import app.voidnotes.data.MarkdownIO
import app.voidnotes.data.NewPageMode
import app.voidnotes.data.Page
import app.voidnotes.data.PdfOptions
import app.voidnotes.data.PdfWriter
import app.voidnotes.data.Release
import app.voidnotes.data.SettingsRepository
import app.voidnotes.data.Store
import app.voidnotes.data.StoredFile
import app.voidnotes.data.Template
import app.voidnotes.data.Updater
import app.voidnotes.data.instantiate
import app.voidnotes.data.newId
import app.voidnotes.data.toTemplate
import app.voidnotes.ui.editor.EditTarget
import app.voidnotes.ui.editor.EditorState
import app.voidnotes.ui.editor.TITLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class PageScreen(val id: String) : Screen
    data class TemplateScreen(val id: String) : Screen
    data object Search : Screen
    data object Templates : Screen
    data object Trash : Screen
    data object Settings : Screen
}

/** Dialogs shown above everything else. */
sealed interface Dialog {
    data class Rename(val id: String) : Dialog
    data class Icon(val id: String) : Dialog
    data class Move(val id: String) : Dialog
    data class SaveTemplate(val id: String) : Dialog
    data class TemplateInfo(val id: String) : Dialog
    data class DeleteTemplate(val id: String) : Dialog
    data class DeleteForever(val id: String) : Dialog
    data object EmptyTrash : Dialog
    data object EditorIcon : Dialog
    data class CalloutEmoji(val blockId: String) : Dialog
    data class RenameFile(val blockId: String) : Dialog
    data class Share(val id: String) : Dialog
    data class StopShare(val id: String) : Dialog
    data object OpenLink : Dialog
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Install(val file: File) : UiEvent
}

/** A file to share, print or show, prepared in the background. */
data class Prepared(val file: File, val mime: String, val pages: Int = 0)

data class Download(val version: String, val done: Long, val total: Long)

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store.get(app)
    private val settingsRepo = SettingsRepository(app)
    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val current: AppSettings get() = settings.value

    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
        private set
    val screen: Screen get() = stack.last()

    var editor by mutableStateOf<EditorState?>(null)
        private set

    /** Template picker for a new page: the parent id ("" = top level). */
    var newPageParent by mutableStateOf<String?>(null)
    var searchQuery by mutableStateOf("")
    var dialog by mutableStateOf<Dialog?>(null)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Activity callbacks (keyboard shortcuts). */
    var keyHandler: ((android.view.KeyEvent) -> Boolean)? = null

    init {
        cleanTrash()
        if (current.autoUpdateCheck) {
            viewModelScope.launch {
                delay(2500)
                checkForUpdates(manual = false)
            }
        }
    }

    fun message(text: String) {
        _events.trySend(UiEvent.Message(text))
    }

    fun editSettings(change: (AppSettings) -> AppSettings) = settingsRepo.edit(change)

    // ------------------------------------------------------------------ navigation

    fun navigate(s: Screen) {
        commitEditor()
        if (s == Screen.Home) {
            stack = listOf(Screen.Home); closeEditor(); return
        }
        // Tabs replace each other instead of piling up.
        val tabs = setOf(Screen.Search, Screen.Templates, Screen.Trash, Screen.Settings)
        stack = if (s in tabs) listOf(Screen.Home, s) else (stack.filterNot { it == s } + s)
        prepareEditor()
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        commitEditor()
        stack = stack.dropLast(1)
        prepareEditor()
        return true
    }

    /** Builds the editor for the screen on top of the stack. */
    private fun prepareEditor() {
        val s = screen
        val target = when (s) {
            is Screen.PageScreen -> EditTarget.PageTarget(s.id)
            is Screen.TemplateScreen -> EditTarget.TemplateTarget(s.id)
            else -> null
        }
        if (target == null) {
            closeEditor(); return
        }
        if (editor?.target == target) return
        closeEditor()
        editor = when (target) {
            is EditTarget.PageTarget -> {
                val p = store.page(target.id) ?: run { stack = stack.dropLast(1); return }
                store.update(p.id) { it.copy(opened = System.currentTimeMillis()) }
                EditorState(target, p.title, p.icon, p.blocks.ifEmpty { listOf(Block()) }, ::scheduleSave).also {
                    editor = it
                    startSync(p)
                }
            }
            is EditTarget.TemplateTarget -> {
                val t = store.templates.value.firstOrNull { it.id == target.id } ?: run { stack = stack.dropLast(1); return }
                EditorState(target, t.title, t.icon, t.blocks.ifEmpty { listOf(Block()) }, ::scheduleSave)
            }
        }
    }

    private fun closeEditor() {
        commitEditor()
        stopSync()
        editor = null
    }

    fun openPage(id: String) {
        val p = store.page(id) ?: return message("Seite nicht gefunden")
        if (p.inTrash) return message("Diese Seite liegt im Papierkorb")
        navigate(Screen.PageScreen(id))
    }

    // ------------------------------------------------------------------ saving

    private var saveJob: Job? = null

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            commitEditor()
        }
    }

    /** Writes the editor content into the store (only when something changed). */
    fun commitEditor() {
        saveJob?.cancel()
        val e = editor ?: return
        val blocks = e.snapshotBlocks()
        val title = e.title.text
        when (val t = e.target) {
            is EditTarget.PageTarget -> {
                val p = store.page(t.id) ?: return
                if (p.blocks == blocks && p.title == title && p.icon == e.icon) return
                store.put(p.copy(title = title, icon = e.icon, blocks = blocks, updated = System.currentTimeMillis()))
                if (sync?.pageId == p.id) sync?.localChanged()
            }
            is EditTarget.TemplateTarget -> {
                val tp = store.templates.value.firstOrNull { it.id == t.id } ?: return
                if (tp.blocks == blocks && tp.title == title && tp.icon == e.icon) return
                store.putTemplate(tp.copy(title = title, icon = e.icon, blocks = blocks, updated = System.currentTimeMillis()))
            }
        }
    }

    fun onStop() {
        commitEditor()
        store.flush()
        sync?.stop()
        autoBackup(force = false)
    }

    fun onStart() {
        sync?.start()
    }

    // ------------------------------------------------------------------ sharing (Firebase)

    /** Live sync of the open shared page. */
    var sync by mutableStateOf<SyncSession?>(null)
        private set

    /** Database used for new shares: from the settings or built into the app. */
    fun syncHost(): String = normalizeHost(current.syncUrl.ifBlank { BuildConfig.SYNC_URL })

    private fun startSync(p: Page) {
        stopSync()
        val info = p.share ?: return
        sync = SyncSession(
            p.id, info, store, viewModelScope,
            local = {
                commitEditor()
                store.page(p.id)
            },
            apply = { merged ->
                store.put(merged)
                editor?.takeIf { it.target == EditTarget.PageTarget(p.id) }?.applyExternal(merged.title, merged.icon, merged.blocks)
            },
            focused = { editor?.focusedId },
        ).also { it.start() }
    }

    private fun stopSync() {
        sync?.stop()
        sync = null
    }

    fun startSharing(id: String) {
        val host = syncHost()
        if (host.isBlank()) {
            message("Erst unter Einstellungen → Teilen die Firebase-Datenbank eintragen")
            navigate(Screen.Settings)
            return
        }
        commitEditor()
        val p = store.page(id) ?: return
        if (p.share == null) {
            val info = ShareInfo(host, Crypto.newId(), Crypto.newKey())
            store.saveSync(id, app.voidnotes.data.SyncMemory())
            store.put(p.copy(share = info))
            if (editor?.target == EditTarget.PageTarget(id)) store.page(id)?.let { startSync(it) }
        }
        dialog = Dialog.Share(id)
    }

    fun stopSharing(id: String, deleteOnline: Boolean) {
        val p = store.page(id) ?: return
        val info = p.share ?: return
        if (sync?.pageId == id) stopSync()
        store.put(p.copy(share = null))
        store.deleteSync(id)
        if (deleteOnline) {
            viewModelScope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { SyncSession.deleteRemote(info) } }
                message(if (r.isSuccess) "Online-Kopie gelöscht – der Link funktioniert nicht mehr" else "Online-Kopie konnte nicht gelöscht werden: ${r.exceptionOrNull()?.message}")
            }
        } else {
            message("Freigabe auf diesem Gerät beendet")
        }
    }

    /** Opens a share link: joins the note (or opens it when it is already here). */
    fun joinLink(text: String?): Boolean {
        val info = text?.let { ShareInfo.parse(it) } ?: run {
            message("Das ist kein gültiger VOID-Notes-Link"); return false
        }
        val existing = store.pages.value.values.firstOrNull { it.share?.id == info.id && !it.inTrash }
        if (existing != null) {
            openPage(existing.id); return true
        }
        commitEditor()
        val page = Page(
            title = "Geteilte Notiz", icon = "🔗", share = info, blocks = listOf(Block()),
            order = (children(null).maxOfOrNull { it.order } ?: 0L) + 1,
        )
        store.put(page)
        navigate(Screen.PageScreen(page.id))
        message("Geteilte Notiz wird geladen …")
        return true
    }

    // ------------------------------------------------------------------ automatic backup

    var backupRunning by mutableStateOf(false)
        private set

    /** Full ZIP into the chosen folder; on leaving the app at most every 10 minutes, and only after changes. */
    fun autoBackup(force: Boolean) {
        val uri = current.autoBackupUri
        if (uri.isBlank() || backupRunning) return
        val latest = maxOf(
            store.lastChange,
            store.pages.value.values.maxOfOrNull { it.updated } ?: 0L,
            store.templates.value.maxOfOrNull { it.updated } ?: 0L,
        )
        val now = System.currentTimeMillis()
        if (!force && (latest <= current.autoBackupLast || now - current.autoBackupLast < 10 * 60_000L)) return
        backupRunning = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { AutoBackup.run(getApplication(), store, uri) } }
            backupRunning = false
            r.onSuccess {
                editSettings { it.copy(autoBackupLast = System.currentTimeMillis()) }
                if (force) message("Gesichert: $it")
            }.onFailure { message("Automatische Sicherung fehlgeschlagen: ${it.message}") }
        }
    }

    fun setBackupFolder(uri: Uri?) {
        if (uri == null) return
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        editSettings { it.copy(autoBackupUri = uri.toString(), autoBackupLast = 0) }
        autoBackup(force = true)
    }

    fun clearBackupFolder() {
        val old = current.autoBackupUri
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        editSettings { it.copy(autoBackupUri = "") }
    }

    /** Changes a page; goes through the editor when that page is open. */
    private fun changePage(id: String, editorChange: ((EditorState) -> Unit)?, change: (Page) -> Page) {
        val e = editor
        if (e != null && e.target == EditTarget.PageTarget(id) && editorChange != null) {
            editorChange(e)
            commitEditor()
        } else {
            store.update(id, change)
        }
    }

    // ------------------------------------------------------------------ pages

    fun children(parentId: String?, pages: Map<String, Page> = store.pages.value): List<Page> =
        pages.values.filter { it.parentId == parentId && !it.inTrash }.sortedBy { it.order }

    fun descendants(id: String, pages: Map<String, Page> = store.pages.value): List<Page> {
        val out = ArrayList<Page>()
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            pages.values.filter { it.parentId == cur }.forEach { out += it; queue += it.id }
        }
        return out
    }

    /** Parent chain from the top down, without the page itself. */
    fun ancestors(id: String): List<Page> {
        val out = ArrayList<Page>()
        var p = store.page(id)?.parentId?.let { store.page(it) }
        val seen = HashSet<String>()
        while (p != null && seen.add(p.id)) {
            out.add(0, p)
            p = p.parentId?.let { store.page(it) }
        }
        return out
    }

    /** "+" / Ctrl+N: either a blank page or the template picker, depending on the settings. */
    fun requestNewPage(parentId: String?) {
        if (current.newPage == NewPageMode.ASK) newPageParent = parentId ?: "" else newPage(parentId, null)
    }

    fun newPage(parentId: String?, template: Template?) {
        commitEditor()
        newPageParent = null
        val siblings = children(parentId)
        val order = (siblings.maxOfOrNull { it.order } ?: 0L) + 1
        val page = (template?.instantiate(parentId) ?: Page(parentId = parentId, blocks = listOf(Block()))).copy(order = order)
        store.put(page)
        if (parentId != null) addPageLink(parentId, page.id)
        if (parentId != null) editSettings { it.copy(expanded = it.expanded + parentId) }
        navigate(Screen.PageScreen(page.id))
        editor?.let { e ->
            if (template == null || page.title.isBlank()) e.requestFocus(TITLE) else e.blocks.firstOrNull { it.type.hasText }?.let { e.requestFocus(it.id) }
        }
    }

    /** Sub page from inside the editor (menu or "/seite"): link right where the cursor is. */
    fun newSubPage(parentId: String) {
        val e = editor ?: return newPage(parentId, null)
        val order = (children(parentId).maxOfOrNull { it.order } ?: 0L) + 1
        val page = Page(parentId = parentId, blocks = listOf(Block()), order = order)
        store.put(page)
        e.insertBlocks(listOf(Block(type = BlockType.PAGE, pageId = page.id)))
        commitEditor()
        editSettings { it.copy(expanded = it.expanded + parentId) }
        navigate(Screen.PageScreen(page.id))
        editor?.requestFocus(TITLE)
    }

    private fun addPageLink(parentId: String, childId: String) {
        val link = Block(type = BlockType.PAGE, pageId = childId)
        changePage(parentId, { it.appendBlock(link) }) { p ->
            if (p.blocks.any { it.pageId == childId }) p else p.copy(blocks = p.blocks + link)
        }
    }

    private fun removePageLink(parentId: String, childId: String) {
        changePage(parentId, { e ->
            e.blocks.filter { it.type == BlockType.PAGE && it.pageId == childId }.forEach { e.blocks.remove(it) }
            e.changed()
        }) { p -> p.copy(blocks = p.blocks.filterNot { it.type == BlockType.PAGE && it.pageId == childId }) }
    }

    fun setIcon(pageId: String, icon: String) {
        changePage(pageId, { it.checkpoint(); it.icon = icon; it.changed() }) { it.copy(icon = icon) }
    }

    fun setTemplateIcon(icon: String) {
        editor?.let { it.checkpoint(); it.icon = icon; it.changed() }
    }

    fun rename(pageId: String, title: String) {
        changePage(pageId, { e ->
            e.title = androidx.compose.ui.text.input.TextFieldValue(title, androidx.compose.ui.text.TextRange(title.length)); e.changed()
        }) { it.copy(title = title, updated = System.currentTimeMillis()) }
    }

    fun toggleFavorite(id: String) {
        val p = store.page(id) ?: return
        store.put(p.copy(favorite = !p.favorite))
        message(if (p.favorite) "Aus Favoriten entfernt" else "Zu Favoriten hinzugefügt")
    }

    fun toggleWide(id: String) {
        val p = store.page(id) ?: return
        store.put(p.copy(wide = !p.wide))
    }

    fun toggleExpanded(id: String) = editSettings { s ->
        s.copy(expanded = if (id in s.expanded) s.expanded - id else s.expanded + id)
    }

    fun toggleSection(key: String) = editSettings { s ->
        s.copy(collapsedSections = if (key in s.collapsedSections) s.collapsedSections - key else s.collapsedSections + key)
    }

    fun duplicate(id: String) {
        commitEditor()
        val p = store.page(id) ?: return
        val copy = p.copy(
            id = newId(), title = if (p.title.isBlank()) "" else p.title + " (Kopie)", favorite = false, share = null,
            blocks = p.blocks.filterNot { it.type == BlockType.PAGE }.map { it.copy(id = newId()) },
            created = System.currentTimeMillis(), updated = System.currentTimeMillis(), order = p.order + 1, opened = 0,
        )
        // Make room right after the original.
        children(p.parentId).filter { it.order > p.order }.forEach { store.put(it.copy(order = it.order + 2)) }
        store.put(copy.copy(order = p.order + 1))
        p.parentId?.let { addPageLink(it, copy.id) }
        message("Seite dupliziert")
    }

    /** Moves a page among its siblings. */
    fun reorder(id: String, delta: Int) {
        val p = store.page(id) ?: return
        val sib = children(p.parentId)
        val i = sib.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in sib.indices) return
        val other = sib[j]
        store.put(p.copy(order = other.order))
        store.put(other.copy(order = p.order))
    }

    fun canMoveTo(id: String, newParent: String?): Boolean =
        newParent != id && (newParent == null || descendants(id).none { it.id == newParent })

    fun move(id: String, newParent: String?) {
        commitEditor()
        val p = store.page(id) ?: return
        if (!canMoveTo(id, newParent)) return message("Eine Seite kann nicht in sich selbst verschoben werden")
        if (p.parentId == newParent) return
        p.parentId?.let { removePageLink(it, id) }
        val order = (children(newParent).maxOfOrNull { it.order } ?: 0L) + 1
        store.put(p.copy(parentId = newParent, order = order))
        newParent?.let {
            addPageLink(it, id)
            editSettings { s -> s.copy(expanded = s.expanded + it) }
        }
        message("Verschoben nach ${newParent?.let { store.page(it)?.displayTitle } ?: "Privat"}")
    }

    // ------------------------------------------------------------------ trash

    fun trash(id: String) {
        commitEditor()
        val p = store.page(id) ?: return
        val now = System.currentTimeMillis()
        val all = listOf(p) + descendants(id).filterNot { it.inTrash }
        all.forEach { store.put(it.copy(trashed = now)) }
        val ids = all.map { it.id }.toSet()
        if (stack.any { it is Screen.PageScreen && it.id in ids }) {
            stack = stack.filterNot { it is Screen.PageScreen && it.id in ids }.ifEmpty { listOf(Screen.Home) }
            prepareEditor()
        }
        message(if (all.size > 1) "„${p.displayTitle}“ und ${all.size - 1} Unterseiten in den Papierkorb" else "„${p.displayTitle}“ in den Papierkorb verschoben")
    }

    fun restore(id: String) {
        val p = store.page(id) ?: return
        val stamp = p.trashed
        val parentOk = p.parentId?.let { store.page(it)?.let { pp -> !pp.inTrash } } ?: true
        store.put(p.copy(trashed = 0, parentId = if (parentOk) p.parentId else null))
        descendants(id).filter { it.trashed == stamp }.forEach { store.put(it.copy(trashed = 0)) }
        message("„${p.displayTitle}“ wiederhergestellt")
    }

    fun deleteForever(id: String) {
        val ids = listOf(id) + descendants(id).map { it.id }
        store.deleteForever(ids)
    }

    fun emptyTrash() {
        val ids = store.pages.value.values.filter { it.inTrash }.map { it.id }
        store.deleteForever(ids)
        message("Papierkorb geleert")
    }

    /** Pages in the trash for more than 30 days are removed. */
    private fun cleanTrash() {
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000
        val old = store.pages.value.values.filter { it.inTrash && it.trashed < cutoff }.map { it.id }
        if (old.isNotEmpty()) store.deleteForever(old) else viewModelScope.launch(Dispatchers.IO) { store.collectGarbage() }
    }

    // ------------------------------------------------------------------ templates

    fun saveAsTemplate(pageId: String, name: String, description: String) {
        commitEditor()
        val p = store.page(pageId) ?: return
        val order = (store.templates.value.maxOfOrNull { it.order } ?: 0L) + 1
        store.putTemplate(p.toTemplate(name.ifBlank { p.displayTitle }, description).copy(order = order))
        message("Vorlage „${name.ifBlank { p.displayTitle }}“ gespeichert")
    }

    fun newTemplate() {
        val order = (store.templates.value.maxOfOrNull { it.order } ?: 0L) + 1
        val t = Template(name = "Neue Vorlage", title = "", blocks = listOf(Block()), order = order)
        store.putTemplate(t)
        navigate(Screen.TemplateScreen(t.id))
        editor?.requestFocus(TITLE)
    }

    fun editTemplate(id: String) = navigate(Screen.TemplateScreen(id))

    fun updateTemplateInfo(id: String, name: String, description: String) {
        commitEditor()
        val t = store.templates.value.firstOrNull { it.id == id } ?: return
        store.putTemplate(t.copy(name = name.ifBlank { t.name }, description = description))
    }

    fun deleteTemplate(id: String) {
        if (editor?.target == EditTarget.TemplateTarget(id)) {
            editor = null
            stack = stack.filterNot { it == Screen.TemplateScreen(id) }
        }
        val name = store.templates.value.firstOrNull { it.id == id }?.name
        store.deleteTemplate(id)
        message("Vorlage „$name“ gelöscht")
    }

    fun duplicateTemplate(id: String) {
        val t = store.templates.value.firstOrNull { it.id == id } ?: return
        store.putTemplate(t.copy(id = newId(), name = t.name + " (Kopie)", order = t.order + 1, blocks = t.blocks.map { it.copy(id = newId()) }))
    }

    fun moveTemplate(id: String, delta: Int) {
        val list = store.templates.value
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        store.putTemplate(list[i].copy(order = list[j].order))
        store.putTemplate(list[j].copy(order = list[i].order))
    }

    fun restoreTemplates() {
        store.restoreBuiltInTemplates()
        message("Standardvorlagen wiederhergestellt")
    }

    /** New page from a template while a page is open: as sub page of nothing (top level). */
    fun useTemplate(t: Template) = newPage(null, t)

    // ------------------------------------------------------------------ attachments

    fun attach(uris: List<Uri>, forcePage: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) {
                uris.mapNotNull { u -> runCatching { store.importFile(u) }.onFailure { message("Datei konnte nicht angehängt werden: ${it.message}") }.getOrNull() }
            }
            if (stored.isEmpty()) return@launch
            val blocks = stored.map { it.toBlock() }
            val e = editor
            if (e == null || forcePage) {
                val page = Page(title = stored.singleOrNull()?.name?.substringBeforeLast('.') ?: "Anhänge", blocks = blocks + Block())
                store.put(page.copy(order = (children(null).maxOfOrNull { it.order } ?: 0L) + 1))
                navigate(Screen.PageScreen(page.id))
            } else {
                e.insertBlocks(blocks)
                commitEditor()
            }
            if (stored.size > 1) message("${stored.size} Dateien angehängt")
        }
    }

    private fun StoredFile.toBlock() = Block(
        type = if (isImage) BlockType.IMAGE else BlockType.FILE, file = file, fileName = name, mime = mime, size = size,
    )

    private var pendingPhoto: File? = null

    fun newPhotoTarget(): File = store.newPhotoFile().also { pendingPhoto = it }

    fun photoTaken(success: Boolean) {
        val f = pendingPhoto ?: return
        pendingPhoto = null
        if (!success || !f.exists() || f.length() == 0L) {
            f.delete(); return
        }
        val name = "Foto ${java.text.SimpleDateFormat("dd.MM.yyyy HH.mm", java.util.Locale.GERMAN).format(java.util.Date())}.jpg"
        val block = Block(type = BlockType.IMAGE, file = f.name, fileName = name, mime = "image/jpeg", size = f.length())
        val e = editor
        if (e != null) {
            e.insertBlocks(listOf(block)); commitEditor()
        }
    }

    fun renameAttachment(blockId: String, name: String) {
        val e = editor ?: return
        val b = e.block(blockId) ?: return
        e.checkpoint()
        b.fileName = name
        e.changed()
    }

    // ------------------------------------------------------------------ shared from other apps

    fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> joinLink(intent.dataString)
            Intent.ACTION_SEND -> {
                val stream = intent.parcelable<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (stream != null) attach(listOf(stream), forcePage = true)
                else if (text != null && ShareInfo.parse(text) != null) joinLink(text)
                else if (!text.isNullOrBlank()) newPageFromText(subject, text)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!list.isNullOrEmpty()) attach(list, forcePage = true)
            }
        }
    }

    private fun newPageFromText(subject: String?, text: String) {
        commitEditor()
        val (title, blocks) = MarkdownIO.import(text, extractTitle = false)
        val page = Page(
            title = subject ?: title.ifBlank { text.lineSequence().first().take(60) },
            blocks = blocks.ifEmpty { listOf(Block()) },
            order = (children(null).maxOfOrNull { it.order } ?: 0L) + 1,
        )
        store.put(page)
        navigate(Screen.PageScreen(page.id))
        message("Als neue Seite gespeichert")
    }

    // ------------------------------------------------------------------ export

    private fun safeName(p: Page) = p.displayTitle.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").trim().take(80).ifBlank { "Notiz" }

    fun fileName(id: String, ext: String) = safeName(store.page(id) ?: Page()) + "." + ext

    private fun pdfOptions() = PdfOptions(current.pdfIncludeIcon, current.pdfPageNumbers, current.accent.argb.toInt().let {
        if (current.accent == app.voidnotes.data.Accent.MONO) 0xFF111111.toInt() else it
    })

    /** Renders the page as PDF / Markdown / text into the cache for sharing and printing. */
    fun prepare(id: String, kind: String, done: (Prepared) -> Unit) {
        commitEditor()
        val p = store.page(id) ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(getApplication<Application>().cacheDir, "export").apply { mkdirs() }
                    dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000) it.delete() }
                    when (kind) {
                        "pdf" -> {
                            val f = File(dir, safeName(p) + ".pdf")
                            val n = f.outputStream().use { PdfWriter(getApplication(), store, pdfOptions()).render(p, store.pages.value, it) }
                            Prepared(f, "application/pdf", n)
                        }
                        "md" -> {
                            val f = File(dir, safeName(p) + ".md")
                            f.writeText(MarkdownIO.export(p, store.pages.value))
                            Prepared(f, "text/markdown")
                        }
                        else -> {
                            val f = File(dir, safeName(p) + ".txt")
                            f.writeText(MarkdownIO.plainText(p, store.pages.value))
                            Prepared(f, "text/plain")
                        }
                    }
                }
            }
            result.onSuccess(done).onFailure { message("Export fehlgeschlagen: ${it.message}") }
        }
    }

    fun exportTo(id: String, kind: String, uri: Uri) {
        prepare(id, kind) { prepared ->
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            prepared.file.inputStream().use { it.copyTo(out) }
                        } ?: error("Ziel kann nicht beschrieben werden")
                    }
                }
                ok.onSuccess { message(if (kind == "pdf") "PDF gespeichert (${prepared.pages} Seiten)" else "Exportiert") }
                    .onFailure { message("Speichern fehlgeschlagen: ${it.message}") }
            }
        }
    }

    fun importMarkdown(uris: List<Uri>) {
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@runCatching null
                        val (title, blocks) = MarkdownIO.import(text)
                        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
                        Page(title = title.ifBlank { name }, blocks = blocks.ifEmpty { listOf(Block()) })
                    }.getOrNull()
                }
            }
            var order = (children(null).maxOfOrNull { it.order } ?: 0L) + 1
            created.forEach { store.put(it.copy(order = order++)) }
            message(if (created.size == 1) "„${created[0].displayTitle}“ importiert" else "${created.size} Seiten importiert")
            created.singleOrNull()?.let { navigate(Screen.PageScreen(it.id)) }
        }
    }

    /** Progress text of a running Notion import (null = none). */
    var importStatus by mutableStateOf<String?>(null)
        private set

    fun importNotion(uri: Uri?) {
        if (uri == null || importStatus != null) return
        commitEditor()
        importStatus = "Starte …"
        viewModelScope.launch {
            val app = getApplication<Application>()
            val work = File(app.cacheDir, "notion-import")
            val r: Result<app.voidnotes.data.NotionResult> = withContext(Dispatchers.IO) {
                val result = runCatching {
                    val input = app.contentResolver.openInputStream(uri) ?: error("Datei kann nicht gelesen werden")
                    input.use { stream ->
                        app.voidnotes.data.NotionImport(store, work) { text -> importStatus = text }.run(stream)
                    }
                }
                work.deleteRecursively()
                result
            }
            importStatus = null
            r.onSuccess { result ->
                // Imported top level pages come after the existing ones.
                var order = (children(null).maxOfOrNull { it.order } ?: 0L) + 1
                val roots = result.rootIds.toSet()
                result.pages.forEach { p -> store.put(if (p.id in roots) p.copy(order = order++) else p) }
                editSettings { s -> s.copy(expanded = s.expanded + result.rootIds) }
                message("${result.pages.size} Seiten und ${result.attachments} Anhänge aus Notion importiert")
                navigate(Screen.Home)
            }.onFailure { message("Notion-Import fehlgeschlagen: ${it.message}") }
        }
    }

    fun backupTo(uri: Uri) {
        commitEditor()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { store.writeBackup(it) } ?: error("Ziel kann nicht beschrieben werden")
                }
            }
            r.onSuccess { message("Sicherung gespeichert") }.onFailure { message("Sicherung fehlgeschlagen: ${it.message}") }
        }
    }

    fun restoreFrom(uri: Uri) {
        commitEditor()
        stack = listOf(Screen.Home, Screen.Settings)
        editor = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { store.readBackup(it) } ?: error("Datei kann nicht gelesen werden")
                }
            }
            r.onSuccess { message("$it Seiten wiederhergestellt") }.onFailure { message("Wiederherstellen fehlgeschlagen: ${it.message}") }
        }
    }

    // ------------------------------------------------------------------ updates

    var releases by mutableStateOf<List<Release>>(emptyList())
        private set
    var availableUpdate by mutableStateOf<Release?>(null)
        private set
    var updateStatus by mutableStateOf<String?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set
    var showUpdateDialog by mutableStateOf(false)
    var download by mutableStateOf<Download?>(null)
        private set
    private var downloadJob: Job? = null

    fun checkForUpdates(manual: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        if (manual) updateStatus = "Suche nach Updates …"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Updater.fetchReleases() } }
            checkingUpdate = false
            result.onSuccess { list ->
                releases = list
                val newest = list.firstOrNull { it.apkUrl != null }
                if (newest != null && Updater.isNewer(newest.version, BuildConfig.VERSION_NAME)) {
                    availableUpdate = newest
                    updateStatus = "Version ${newest.version} ist verfügbar"
                    if (!manual) showUpdateDialog = true
                } else {
                    availableUpdate = null
                    updateStatus = "VOID Notes ist aktuell"
                }
            }.onFailure {
                updateStatus = "Update-Prüfung fehlgeschlagen: ${it.message ?: "keine Verbindung"}"
                if (manual) message(updateStatus!!)
            }
        }
    }

    fun downloadUpdate(release: Release) {
        showUpdateDialog = false
        val url = release.apkUrl ?: return message("Für diese Version gibt es keine APK")
        if (downloadJob?.isActive == true) return
        download = Download(release.version, 0, release.apkSize)
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            val self = coroutineContext.job
            val dir = File(getApplication<Application>().cacheDir, "updates")
            val file = File(dir, "VOID-Notes-${release.version}.apk")
            var last = 0L
            try {
                dir.listFiles()?.forEach { it.delete() }
                Updater.download(url, file, { done, total ->
                    val now = System.currentTimeMillis()
                    if (now - last > 120) {
                        last = now
                        download = Download(release.version, done, if (total > 0) total else release.apkSize)
                    }
                }, { !self.isActive })
                download = null
                _events.trySend(UiEvent.Install(file))
            } catch (e: Throwable) {
                download = null
                message(if (!isActive) "Update abgebrochen" else "Update fehlgeschlagen: ${e.message}")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        download = null
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.parcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key) as? T
