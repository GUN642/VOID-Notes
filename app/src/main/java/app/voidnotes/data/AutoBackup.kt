package app.voidnotes.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a full backup (pages, templates, attachments) as ZIP into a folder the user picked –
 * e.g. a Google Drive folder – and keeps the newest few.
 */
object AutoBackup {
    private const val PREFIX = "VOID-Notes-Auto-"
    const val KEEP = 5

    fun folderName(context: Context, uri: String): String =
        runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name }.getOrNull() ?: "Ordner"

    /** Returns the file name written. */
    fun run(context: Context, store: Store, uri: String): String {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: error("Ordner nicht erreichbar")
        if (!dir.canWrite()) error("Kein Schreibzugriff auf den Ordner – bitte neu auswählen")
        val name = PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMAN).format(Date()) + ".zip"
        dir.findFile(name)?.delete()
        val file = dir.createFile("application/zip", name) ?: error("Datei kann nicht angelegt werden")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { store.writeBackup(it) } ?: error("Datei kann nicht geschrieben werden")
        // Keep only the newest backups.
        dir.listFiles().filter { it.name?.startsWith(PREFIX) == true }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { runCatching { it.delete() } }
        return name
    }
}
