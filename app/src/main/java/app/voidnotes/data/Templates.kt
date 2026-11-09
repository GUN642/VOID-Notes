package app.voidnotes.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun t(text: String = "") = Block(type = BlockType.TEXT, text = text)
private fun h2(text: String) = Block(type = BlockType.H2, text = text)
private fun h3(text: String) = Block(type = BlockType.H3, text = text)
private fun bullet(text: String = "", level: Int = 0) = Block(type = BlockType.BULLET, text = text, level = level)
private fun num(text: String = "") = Block(type = BlockType.NUMBERED, text = text)
private fun todo(text: String = "", checked: Boolean = false, level: Int = 0) =
    Block(type = BlockType.TODO, text = text, checked = checked, level = level)
private fun callout(text: String, emoji: String = "💡") = Block(type = BlockType.CALLOUT, text = text, emoji = emoji)
private fun toggle(text: String, collapsed: Boolean = false) = Block(type = BlockType.TOGGLE, text = text, collapsed = collapsed)
private fun quote(text: String) = Block(type = BlockType.QUOTE, text = text)
private fun code(text: String) = Block(type = BlockType.CODE, text = text)
private fun divider() = Block(type = BlockType.DIVIDER)

object Placeholders {
    val help = listOf(
        "{{datum}}" to "Datum, z. B. 09.11.2026",
        "{{zeit}}" to "Uhrzeit, z. B. 14:30",
        "{{wochentag}}" to "Wochentag, z. B. Montag",
        "{{kw}}" to "Kalenderwoche",
        "{{monat}}" to "Monat und Jahr",
    )

    fun fill(text: String, now: Date = Date()): String {
        if (!text.contains("{{")) return text
        val de = Locale.GERMAN
        return text
            .replace("{{datum}}", SimpleDateFormat("dd.MM.yyyy", de).format(now), ignoreCase = true)
            .replace("{{zeit}}", SimpleDateFormat("HH:mm", de).format(now), ignoreCase = true)
            .replace("{{wochentag}}", SimpleDateFormat("EEEE", de).format(now), ignoreCase = true)
            .replace("{{kw}}", SimpleDateFormat("w", de).format(now), ignoreCase = true)
            .replace("{{monat}}", SimpleDateFormat("MMMM yyyy", de).format(now), ignoreCase = true)
    }
}

/** Creates a new page from [template]: new block ids, placeholders filled in. */
fun Template.instantiate(parentId: String?): Page = Page(
    parentId = parentId,
    title = Placeholders.fill(title),
    icon = icon,
    blocks = blocks.map { it.copy(id = newId(), text = Placeholders.fill(it.text), pageId = null) }
        .filterNot { it.type == BlockType.PAGE },
)

/** Turns an existing page into a template. */
fun Page.toTemplate(name: String, description: String): Template = Template(
    name = name,
    description = description,
    icon = icon,
    title = title,
    blocks = blocks.filterNot { it.type == BlockType.PAGE }.map { it.copy(id = newId()) },
)

object BuiltInTemplates {
    fun all(): List<Template> {
        var order = 1L
        return listOf(
            Template(
                name = "Checkliste", icon = "✅", title = "Checkliste", order = order++,
                description = "Abhaken, was erledigt ist",
                blocks = listOf(
                    callout("Tippe auf einen Kreis, um einen Punkt abzuhaken.", "☑️"),
                    todo(), todo(), todo(), todo(), todo(),
                ),
            ),
            Template(
                name = "Besprechung", icon = "🗓️", title = "Besprechung {{datum}}", order = order++,
                description = "Agenda, Notizen, Beschlüsse und Aufgaben",
                blocks = listOf(
                    t("**Datum:** {{wochentag}}, {{datum}} · {{zeit}} Uhr"),
                    t("**Ort:** "),
                    h2("Teilnehmer"),
                    bullet(),
                    h2("Agenda"),
                    num(), num(), num(),
                    h2("Notizen"),
                    t(),
                    h2("Beschlüsse"),
                    bullet(),
                    h2("Aufgaben"),
                    todo("Aufgabe – **wer:** … – **bis:** …"),
                    todo(),
                    h2("Nächster Termin"),
                    t(),
                ),
            ),
            Template(
                name = "Aufgaben", icon = "📋", title = "Aufgaben", order = order++,
                description = "Heute, diese Woche, später",
                blocks = listOf(
                    h2("Heute"), todo(), todo(),
                    h2("Diese Woche"), todo(), todo(),
                    h2("Später"), todo(),
                    toggle("Erledigt", collapsed = true),
                ),
            ),
            Template(
                name = "Tagebuch", icon = "📓", title = "{{wochentag}}, {{datum}}", order = order++,
                description = "Ein Eintrag pro Tag",
                blocks = listOf(
                    h2("Was heute los war"), t(),
                    h2("Dankbar für"), bullet(), bullet(), bullet(),
                    h2("Morgen"), todo(),
                ),
            ),
            Template(
                name = "Projekt", icon = "🚀", title = "Projekt", order = order++,
                description = "Ziel, Meilensteine, Ressourcen",
                blocks = listOf(
                    callout("**Status:** In Planung", "📌"),
                    h2("Ziel"), t(),
                    h2("Meilensteine"), todo(), todo(), todo(),
                    h2("Ressourcen & Links"), bullet(),
                    h2("Risiken"), bullet(),
                    h2("Notizen"), t(),
                ),
            ),
            Template(
                name = "Briefing", icon = "🧭", title = "Briefing {{datum}}", order = order++,
                description = "Auftrag, Lage, Ablauf, Risiken",
                blocks = listOf(
                    t("**Datum:** {{datum}} · **Zeit:** {{zeit}}"),
                    h2("Auftrag"), t(),
                    h2("Lage & Wetter"), bullet("Wetter: "), bullet("Hinweise / NOTAM: "),
                    h2("Besatzung / Team"), bullet(),
                    h2("Ablauf"), num(), num(), num(),
                    h2("Risiken & Maßnahmen"), bullet(),
                    h2("Nachbesprechung"),
                    toggle("Was lief gut?"), bullet(level = 1),
                    toggle("Was verbessern wir?"), bullet(level = 1),
                ),
            ),
            Template(
                name = "Kurs / Schulung", icon = "🎓", title = "Kurs: ", order = order++,
                description = "Mitschrift mit Kernaussagen und offenen Fragen",
                blocks = listOf(
                    t("**Datum:** {{datum}} · **Dozent:** "),
                    h2("Kernaussagen"), bullet(),
                    h2("Mitschrift"), t(),
                    h2("Offene Fragen"), bullet(),
                    h2("To-dos"), todo(),
                ),
            ),
        )
    }
}

/** Pages shown on the very first start. */
object Welcome {
    fun pages(): List<Page> {
        val tips = Page(
            title = "Tastenkürzel & Tricks", icon = "⌨️", order = 2,
            blocks = listOf(
                h2("Schnell formatieren"),
                t("Tippe am Zeilenanfang:"),
                bullet("`# ` `## ` `### ` → Überschriften"),
                bullet("`- ` → Aufzählung, `1. ` → Nummerierung"),
                bullet("`[] ` → Checkbox, `> ` → aufklappbar, `\" ` → Zitat"),
                bullet("`! ` → Hinweis, ` ``` ` → Code, `---` → Trennlinie"),
                bullet("`/` → alle Blocktypen"),
                t("Im Text: `**fett**`, `*kursiv*`, `~~durchgestrichen~~`, `==markiert==`, `` `Code` ``"),
                h2("Samsung DeX & Tastatur"),
                bullet("**Strg+N** neue Seite · **Strg+K** / **Strg+F** Suche"),
                bullet("**Strg+B / I / Umschalt+S / E / Umschalt+H** fett, kursiv, durchgestrichen, Code, markiert"),
                bullet("**Tab / Umschalt+Tab** einrücken"),
                bullet("**Strg+Enter** Checkbox abhaken · **Umschalt+Enter** Zeilenumbruch"),
                bullet("**Alt+↑ / Alt+↓** Block verschieben · **Strg+D** Block duplizieren"),
                bullet("**Strg+P** drucken · **Strg+Umschalt+E** als PDF exportieren"),
                bullet("**Strg+\\** Seitenleiste ein/aus · **Esc** zurück"),
                bullet("Rechtsklick auf eine Seite in der Seitenleiste öffnet ihr Menü"),
                bullet("Dateien aus anderen Apps einfach in eine Seite ziehen"),
            ),
        )
        val welcome = Page(
            title = "Willkommen", icon = "👋", order = 1,
            blocks = listOf(
                callout("VOID Notes speichert alles nur auf diesem Gerät. Sicherungen findest du in den Einstellungen.", "🔒"),
                h2("Los geht's"),
                todo("Neue Seite mit dem Stift unten rechts anlegen"),
                todo("Eine Seite mit dem Stern zu den Favoriten hinzufügen"),
                todo("Eine Vorlage ausprobieren (Tab „Vorlagen“)"),
                todo("Über ⋮ → „Als Vorlage speichern“ eigene Vorlagen anlegen"),
                todo("Eine Datei oder ein Foto anhängen"),
                todo("Seite als PDF exportieren oder drucken"),
                h2("Seiten verschachteln"),
                t("Jede Seite kann Unterseiten haben – wie Ordner. Über ⋮ → „Unterseite“ oder mit `/seite`."),
                toggle("Aufklappbare Abschnitte"),
                t("…verstecken Details, bis du sie brauchst.").copy(level = 1),
                divider(),
                quote("Weniger, aber besser."),
                h3("Code"),
                code("fun hallo() = println(\"VOID\")"),
            ),
        )
        return listOf(
            welcome.copy(blocks = welcome.blocks + Block(type = BlockType.PAGE, pageId = tips.id)),
            tips.copy(parentId = welcome.id),
        )
    }
}
