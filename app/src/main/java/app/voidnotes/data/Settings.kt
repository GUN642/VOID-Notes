package app.voidnotes.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    BLACK("Schwarz"),
    GRAPHITE("Graphit"),
    PAPER("Papier"),
    WHITE("Weiß"),
}

enum class Accent(val label: String, val argb: Long) {
    RED("Rot", 0xFFD71921),
    MONO("Mono", 0xFFFFFFFF),
    YELLOW("Gelb", 0xFFFFC400),
    ORANGE("Orange", 0xFFFF6A13),
    GREEN("Grün", 0xFF34C759),
    BLUE("Blau", 0xFF3D7BFF),
    PINK("Pink", 0xFFFF4F8B),
}

enum class TextSize(val label: String, val scale: Float) { SMALL("Klein", 0.9f), NORMAL("Normal", 1f), LARGE("Groß", 1.15f), XL("Sehr groß", 1.3f) }

/** How wide the text column of a page is on large screens (DeX). */
enum class PageWidth(val label: String, val dp: Int) { NARROW("Schmal", 720), NORMAL("Normal", 900), FULL("Volle Breite", 100_000) }

enum class NewPageMode(val label: String) { BLANK("Leere Seite"), ASK("Vorlage wählen") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.RED,
    val dotHeadings: Boolean = true,
    val dotGrid: Boolean = true,
    val textSize: TextSize = TextSize.NORMAL,
    val pageWidth: PageWidth = PageWidth.NORMAL,
    val newPage: NewPageMode = NewPageMode.BLANK,
    val autoUpdateCheck: Boolean = true,
    val showRecent: Boolean = true,
    val spellCheck: Boolean = true,
    /** Sidebar visible on wide screens. */
    val sidebar: Boolean = true,
    val pdfIncludeIcon: Boolean = true,
    val pdfPageNumbers: Boolean = true,
    /** Sections on the home screen that are folded. */
    val collapsedSections: Set<String> = emptySet(),
    /** Pages expanded in the tree. */
    val expanded: Set<String> = emptySet(),
    /** Firebase Realtime Database for shared notes (empty = the one built into the app). */
    val syncUrl: String = "",
    /** Folder for automatic ZIP backups (tree URI), empty = off. */
    val autoBackupUri: String = "",
    val autoBackupLast: Long = 0,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private inline fun <reified T : Enum<T>> enum(key: String, default: T): T =
        prefs.getString(key, null)?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enum("theme", d.theme),
            accent = enum("accent", d.accent),
            dotHeadings = prefs.getBoolean("dot_headings", d.dotHeadings),
            dotGrid = prefs.getBoolean("dot_grid", d.dotGrid),
            textSize = enum("text_size", d.textSize),
            pageWidth = enum("page_width", d.pageWidth),
            newPage = enum("new_page", d.newPage),
            autoUpdateCheck = prefs.getBoolean("auto_update", d.autoUpdateCheck),
            showRecent = prefs.getBoolean("show_recent", d.showRecent),
            spellCheck = prefs.getBoolean("spell_check", d.spellCheck),
            sidebar = prefs.getBoolean("sidebar", d.sidebar),
            pdfIncludeIcon = prefs.getBoolean("pdf_icon", d.pdfIncludeIcon),
            pdfPageNumbers = prefs.getBoolean("pdf_numbers", d.pdfPageNumbers),
            collapsedSections = prefs.getStringSet("collapsed_sections", null)?.toSet() ?: d.collapsedSections,
            expanded = prefs.getStringSet("expanded", null)?.toSet() ?: d.expanded,
            syncUrl = prefs.getString("sync_url", d.syncUrl) ?: d.syncUrl,
            autoBackupUri = prefs.getString("auto_backup_uri", d.autoBackupUri) ?: d.autoBackupUri,
            autoBackupLast = prefs.getLong("auto_backup_last", d.autoBackupLast),
        )
    }

    fun edit(change: (AppSettings) -> AppSettings) {
        val s = change(_settings.value)
        _settings.value = s
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("accent", s.accent.name)
            .putBoolean("dot_headings", s.dotHeadings)
            .putBoolean("dot_grid", s.dotGrid)
            .putString("text_size", s.textSize.name)
            .putString("page_width", s.pageWidth.name)
            .putString("new_page", s.newPage.name)
            .putBoolean("auto_update", s.autoUpdateCheck)
            .putBoolean("show_recent", s.showRecent)
            .putBoolean("spell_check", s.spellCheck)
            .putBoolean("sidebar", s.sidebar)
            .putBoolean("pdf_icon", s.pdfIncludeIcon)
            .putBoolean("pdf_numbers", s.pdfPageNumbers)
            .putStringSet("collapsed_sections", s.collapsedSections)
            .putStringSet("expanded", s.expanded)
            .putString("sync_url", s.syncUrl)
            .putString("auto_backup_uri", s.autoBackupUri)
            .putLong("auto_backup_last", s.autoBackupLast)
            .apply()
    }
}
