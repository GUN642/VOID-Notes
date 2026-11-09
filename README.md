# VOID Notes

Notizen im Nothing-Stil – aufgebaut wie Notion: Seiten mit Unterseiten, Blöcke, Vorlagen.

## Funktionen
- **Seiten & Unterseiten** als Baum (wie Ordner), Favoriten, „Zuletzt verwendet“, Papierkorb (30 Tage)
- **Block-Editor**: Text, Überschriften, Aufzählung, Nummerierung, Checkbox, aufklappbare Abschnitte, Zitat, Hinweis, Code, Trennlinie, Bilder, Dateien, Unterseiten
- **Schnell formatieren**: `/` öffnet alle Blocktypen; `# `, `- `, `1. `, `[] `, `> `, `" `, `! `, ```` ``` ````, `---` am Zeilenanfang; im Text `**fett**`, `*kursiv*`, `~~durch~~`, `==markiert==`, `` `Code` ``
- **Vorlagen**: Checkliste, Besprechung, Aufgaben, Tagebuch, Projekt, Briefing, Kurs – und eigene (Seite → ⋮ → „Als Vorlage speichern“). Platzhalter `{{datum}}`, `{{zeit}}`, `{{wochentag}}`, `{{kw}}`, `{{monat}}`
- **Anhänge**: Dateien, Bilder aus der Galerie, Kamera-Fotos; Dateien aus anderen Apps teilen oder (DeX) hineinziehen
- **PDF-Export, Drucken, Teilen** (PDF, Markdown, Text), Markdown-Import, Sicherung/Wiederherstellung als ZIP
- **Hell & Dunkel**: System, Schwarz (AMOLED), Graphit, Papier, Weiß; sieben Akzentfarben; Dot-Matrix-Überschriften
- **Samsung DeX**: startet im Vollbild, Seitenleiste + Editor nebeneinander, Maus (Rechtsklick-Menüs, Hover), Tastenkürzel:
  - Strg+N neue Seite · Strg+Umschalt+N Unterseite · Strg+K / Strg+F Suche · Strg+P drucken · Strg+Umschalt+E PDF
  - Strg+B / I / E · Strg+Umschalt+S / H · Tab / Umschalt+Tab · Alt+↑/↓ · Strg+D · Strg+Z / Strg+Y · Strg+Enter · Umschalt+Enter · Strg+\ · Esc
- **Updates in der App**: Einstellungen → Updates (lädt die neueste APK aus den GitHub-Releases)

## APK herunterladen
Jeder Push baut automatisch eine APK – zu finden unter **Releases** bzw. im Actions-Lauf als Artifact.

## Signatur
Standardmäßig wird mit dem Schlüssel in `signing/` signiert, damit Updates sich über die alte Version installieren lassen.
Für einen privaten Schlüssel die Secrets `VOID_KEYSTORE_BASE64`, `VOID_KEYSTORE_PASSWORD`, `VOID_KEY_ALIAS` und `VOID_KEY_PASSWORD` im Repository hinterlegen.
