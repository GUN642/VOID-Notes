package app.voidnotes.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

@Serializable
enum class BlockType(val label: String, val hint: String) {
    TEXT("Text", "Schreibe etwas oder tippe / für Befehle"),
    H1("Überschrift 1", "Überschrift 1"),
    H2("Überschrift 2", "Überschrift 2"),
    H3("Überschrift 3", "Überschrift 3"),
    BULLET("Aufzählung", "Listeneintrag"),
    NUMBERED("Nummerierung", "Listeneintrag"),
    TODO("Checkbox", "Aufgabe"),
    TOGGLE("Aufklappbar", "Aufklappbarer Abschnitt"),
    QUOTE("Zitat", "Zitat"),
    CALLOUT("Hinweis", "Hinweis"),
    CODE("Code", "Code"),
    DIVIDER("Trennlinie", ""),
    IMAGE("Bild", ""),
    FILE("Datei", ""),
    PAGE("Unterseite", "");

    /** Blocks the user types into. */
    val hasText: Boolean get() = this !in setOf(DIVIDER, IMAGE, FILE, PAGE)

    /** Pressing Enter at the end continues with the same kind of block. */
    val continues: Boolean get() = this in setOf(BULLET, NUMBERED, TODO)
}

@Serializable
data class Block(
    val id: String = newId(),
    val type: BlockType = BlockType.TEXT,
    val text: String = "",
    val checked: Boolean = false,
    /** Indentation: nested list items and the content of toggles. */
    val level: Int = 0,
    /** Toggle closed. */
    val collapsed: Boolean = false,
    /** Stored file name inside the attachments folder (IMAGE / FILE). */
    val file: String? = null,
    /** Original name of an attachment as shown to the user. */
    val fileName: String? = null,
    val mime: String? = null,
    val size: Long = 0,
    /** Linked sub page (PAGE). */
    val pageId: String? = null,
    /** Emoji of a callout. */
    val emoji: String? = null,
)

@Serializable
data class Page(
    val id: String = newId(),
    val parentId: String? = null,
    val title: String = "",
    val icon: String = "",
    val blocks: List<Block> = emptyList(),
    val favorite: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val opened: Long = 0,
    /** Position among its siblings. */
    val order: Long = System.currentTimeMillis(),
    /** Moved to the trash at this time (0 = not in the trash). */
    val trashed: Long = 0,
    /** Full page width instead of the reading column. */
    val wide: Boolean = false,
    /** Shared by link: synced live through Firebase. */
    val share: ShareInfo? = null,
) {
    val displayTitle: String get() = title.ifBlank { "Ohne Titel" }
    val inTrash: Boolean get() = trashed > 0
}

@Serializable
data class Template(
    val id: String = newId(),
    val name: String,
    val description: String = "",
    val icon: String = "",
    /** Title of new pages; may contain placeholders like {{datum}}. */
    val title: String = "",
    val blocks: List<Block> = emptyList(),
    val order: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
)

/** Where a shared page lives online and the key that decrypts it. */
@Serializable
data class ShareInfo(val host: String, val id: String, val key: String) {
    val link: String get() = "https://gun642.github.io/VOID-Notes/s/#$host/$id/$key"

    companion object {
        private val pattern = Regex("([a-z0-9.-]+\\.(?:firebaseio\\.com|firebasedatabase\\.app))/([A-Za-z0-9_-]{16,})/([A-Za-z0-9_-]{40,})")

        /** Reads a share link in any of its forms (web link, app link or the bare part). */
        fun parse(text: String): ShareInfo? {
            val decoded = runCatching { java.net.URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
            val m = pattern.find(decoded) ?: return null
            return ShareInfo(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
    }
}
