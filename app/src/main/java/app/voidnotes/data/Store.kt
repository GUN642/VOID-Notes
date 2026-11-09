package app.voidnotes.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Attachment copied into the app. */
data class StoredFile(val file: String, val name: String, val mime: String, val size: Long) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

/**
 * All pages and templates live as small JSON files in the app's private storage and are kept in memory.
 * Changes are written shortly after they happen (and immediately on [flush]).
 */
class Store private constructor(private val context: Context) {
    private val root = File(context.filesDir, "notes")
    private val pageDir = File(root, "pages")
    private val templateDir = File(root, "templates")
    val attachmentDir = File(root, "attachments")
    private val syncDir = File(root, "sync")

    /** Time of the last change to any page or template (for the automatic backup). */
    @Volatile var lastChange: Long = 0L
        private set

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pages = MutableStateFlow<Map<String, Page>>(emptyMap())
    val pages: StateFlow<Map<String, Page>> = _pages.asStateFlow()

    private val _templates = MutableStateFlow<List<Template>>(emptyList())
    val templates: StateFlow<List<Template>> = _templates.asStateFlow()

    private val dirtyPages = HashSet<String>()
    private val dirtyTemplates = HashSet<String>()
    private val lock = Any()
    private var writer: Job? = null
    private var writing = false

    init {
        pageDir.mkdirs(); templateDir.mkdirs(); attachmentDir.mkdirs()
        load()
    }

    private fun load() {
        val pages = pageDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString(Page.serializer(), f.readText()) }.getOrNull()
        }
        _pages.value = pages.associateBy { it.id }
        val seeded = File(root, ".templates_seeded")
        if (!seeded.exists()) {
            BuiltInTemplates.all().forEach { writeTemplate(it) }
            seeded.writeText("1")
        }
        _templates.value = templateDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString(Template.serializer(), f.readText()) }.getOrNull()
        }.sortedBy { it.order }
        if (pages.isEmpty() && !File(root, ".welcome").exists()) {
            File(root, ".welcome").writeText("1")
            Welcome.pages().forEach { put(it) }
        }
    }

    // ------------------------------------------------------------------ pages

    fun page(id: String?): Page? = id?.let { _pages.value[it] }

    fun put(page: Page) {
        _pages.update { it + (page.id to page) }
        markDirty(page.id, template = false)
    }

    fun update(id: String, change: (Page) -> Page) {
        val cur = _pages.value[id] ?: return
        put(change(cur))
    }

    /** Removes pages for good (from the trash) together with attachments nobody uses any more. */
    fun deleteForever(ids: Collection<String>) {
        _pages.update { it - ids.toSet() }
        scope.launch {
            ids.forEach { File(pageDir, "$it.json").delete(); File(syncDir, "$it.json").delete() }
            collectGarbage()
        }
    }

    // ------------------------------------------------------------------ templates

    fun putTemplate(t: Template) {
        _templates.update { list -> (list.filterNot { it.id == t.id } + t).sortedBy { it.order } }
        markDirty(t.id, template = true)
    }

    fun deleteTemplate(id: String) {
        _templates.update { list -> list.filterNot { it.id == id } }
        scope.launch {
            File(templateDir, "$id.json").delete()
            collectGarbage()
        }
    }

    fun restoreBuiltInTemplates() {
        val have = _templates.value.map { it.name }.toSet()
        BuiltInTemplates.all().filter { it.name !in have }.forEach { putTemplate(it) }
    }

    // ------------------------------------------------------------------ writing

    private fun markDirty(id: String, template: Boolean) {
        lastChange = System.currentTimeMillis()
        synchronized(lock) {
            if (template) dirtyTemplates += id else dirtyPages += id
            if (writing) return
            writing = true
        }
        writer = scope.launch {
            while (true) {
                delay(400)
                writeDirty()
                synchronized(lock) {
                    if (dirtyPages.isEmpty() && dirtyTemplates.isEmpty()) {
                        writing = false
                        return@launch
                    }
                }
            }
        }
    }

    private fun writeDirty() {
        val (p, t) = synchronized(lock) {
            val a = dirtyPages.toList(); val b = dirtyTemplates.toList()
            dirtyPages.clear(); dirtyTemplates.clear()
            a to b
        }
        p.forEach { id -> _pages.value[id]?.let { writePage(it) } }
        t.forEach { id -> _templates.value.firstOrNull { it.id == id }?.let { writeTemplate(it) } }
    }

    /** Writes pending changes right away (app goes to the background). */
    fun flush() {
        scope.launch { writeDirty() }
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete(); tmp.renameTo(target)
        }
    }

    private fun writePage(p: Page) = runCatching { writeAtomic(File(pageDir, "${p.id}.json"), json.encodeToString(Page.serializer(), p)) }
    private fun writeTemplate(t: Template) =
        runCatching { writeAtomic(File(templateDir, "${t.id}.json"), json.encodeToString(Template.serializer(), t)) }

    // ------------------------------------------------------------------ sync memory

    fun loadSync(pageId: String): SyncMemory = runCatching {
        json.decodeFromString(SyncMemory.serializer(), File(syncDir, "$pageId.json").readText())
    }.getOrDefault(SyncMemory())

    fun saveSync(pageId: String, memory: SyncMemory) {
        val text = json.encodeToString(SyncMemory.serializer(), memory)
        scope.launch { runCatching { syncDir.mkdirs(); writeAtomic(File(syncDir, "$pageId.json"), text) } }
    }

    fun deleteSync(pageId: String) {
        scope.launch { File(syncDir, "$pageId.json").delete() }
    }

    // ------------------------------------------------------------------ attachments

    fun attachment(name: String): File = File(attachmentDir, name)

    /** Copies a picked or shared file into the app. */
    fun importFile(uri: Uri): StoredFile {
        val resolver = context.contentResolver
        var name = "Datei"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        if (name == "Datei") uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { name = it }
        val mime = resolver.getType(uri) ?: mimeFromName(name)
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 8 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val stored = "${newId()}.$ext"
        val target = attachment(stored)
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("Datei kann nicht gelesen werden")
        return StoredFile(stored, name, mime, if (size > 0) size else target.length())
    }

    /** A fresh attachment file for a camera photo. */
    fun newPhotoFile(): File = attachment("${newId()}.jpg")

    /** Copies a file from disk (e.g. an unpacked Notion export) into the attachments. */
    fun importLocalFile(source: File, displayName: String = source.name): StoredFile {
        val name = displayName
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.length <= 8 } ?: "bin"
        val stored = "${newId()}.$ext"
        source.copyTo(attachment(stored), overwrite = true)
        return StoredFile(stored, name, mimeFromName(name), source.length())
    }

    /** Deletes attachments that no page or template refers to any more. */
    fun collectGarbage() {
        val used = HashSet<String>()
        _pages.value.values.forEach { p -> p.blocks.forEach { b -> b.file?.let(used::add) } }
        _templates.value.forEach { t -> t.blocks.forEach { b -> b.file?.let(used::add) } }
        val cutoff = System.currentTimeMillis() - 10 * 60_000L // photos still being taken
        attachmentDir.listFiles().orEmpty().forEach { f -> if (f.name !in used && f.lastModified() < cutoff) f.delete() }
    }

    // ------------------------------------------------------------------ backup

    fun writeBackup(out: OutputStream) {
        writeDirty()
        ZipOutputStream(out.buffered()).use { zip ->
            fun add(dir: File, prefix: String) {
                dir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                    zip.putNextEntry(ZipEntry(prefix + f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            add(pageDir, "pages/")
            add(templateDir, "templates/")
            add(attachmentDir, "attachments/")
            add(syncDir, "sync/")
        }
    }

    /** Restores a backup: pages and templates with the same id are replaced, everything else is kept. */
    fun readBackup(input: InputStream): Int {
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val dir = when {
                    e.name.startsWith("pages/") -> pageDir
                    e.name.startsWith("templates/") -> templateDir
                    e.name.startsWith("attachments/") -> attachmentDir
                    e.name.startsWith("sync/") -> syncDir.also { it.mkdirs() }
                    else -> null
                } ?: continue
                val fileName = e.name.substringAfterLast('/')
                if (fileName.isBlank() || fileName.contains("..")) continue
                File(dir, fileName).outputStream().use { zip.copyTo(it) }
                if (dir == pageDir) count++
            }
        }
        _pages.value = pageDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString(Page.serializer(), f.readText()) }.getOrNull()
        }.associateBy { it.id }
        _templates.value = templateDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString(Template.serializer(), f.readText()) }.getOrNull()
        }.sortedBy { it.order }
        return count
    }

    companion object {
        @Volatile private var instance: Store? = null

        /** Bumped when an attachment arrives later (shared pages), so pictures redraw. */
        var fileVersion by androidx.compose.runtime.mutableIntStateOf(0)

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }

        fun mimeFromName(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
