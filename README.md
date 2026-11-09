# VOID Notes

Notizen im Nothing-Stil – aufgebaut wie Notion: Seiten mit Unterseiten, Blöcke, Vorlagen.

## Funktionen
- **Seiten & Unterseiten** als Baum (wie Ordner), Favoriten, „Zuletzt verwendet“, Papierkorb (30 Tage)
- **Block-Editor**: Text, Überschriften, Aufzählung, Nummerierung, Checkbox, aufklappbare Abschnitte, Zitat, Hinweis, Code, Trennlinie, Bilder, Dateien, Unterseiten
- **Schnell formatieren**: `/` öffnet alle Blocktypen; `# `, `- `, `1. `, `[] `, `> `, `" `, `! `, ```` ``` ````, `---` am Zeilenanfang; im Text `**fett**`, `*kursiv*`, `~~durch~~`, `==markiert==`, `` `Code` ``
- **Vorlagen**: Checkliste, Besprechung, Aufgaben, Tagebuch, Projekt, Briefing, Kurs – und eigene (Seite → ⋮ → „Als Vorlage speichern“). Platzhalter `{{datum}}`, `{{zeit}}`, `{{wochentag}}`, `{{kw}}`, `{{monat}}`
- **Anhänge**: Dateien, Bilder aus der Galerie, Kamera-Fotos; Dateien aus anderen Apps teilen oder (DeX) hineinziehen
- **Notion-Import**: kompletten Workspace-Export („Markdown & CSV“ als ZIP) mit Unterseiten, Bildern, Dateien und Datenbanken übernehmen (Einstellungen → Import)
- **PDF-Export, Drucken, Teilen** (PDF, Markdown, Text), Markdown-Import, Sicherung/Wiederherstellung als ZIP
- **Hell & Dunkel**: System, Schwarz (AMOLED), Graphit, Papier, Weiß; sieben Akzentfarben; Dot-Matrix-Überschriften; Schriftarten Space Grotesk, Manrope, Inter, DM Sans oder System
- **Samsung DeX**: startet im Vollbild, Seitenleiste + Editor nebeneinander, Maus (Rechtsklick-Menüs, Hover), Tastenkürzel:
  - Strg+N neue Seite · Strg+Umschalt+N Unterseite · Strg+K / Strg+F Suche · Strg+P drucken · Strg+Umschalt+E PDF
  - Strg+B / I / E · Strg+Umschalt+S / H · Tab / Umschalt+Tab · Alt+↑/↓ · Strg+D · Strg+Z / Strg+Y · Strg+Enter · Umschalt+Enter · Strg+\ · Esc
- **Teilen per Link & gemeinsam bearbeiten**: Seite → ⋮ → „Per Link teilen“. Live-Abgleich über eine eigene Firebase Realtime Database, Ende-zu-Ende verschlüsselt (Schlüssel nur im Link), Anhänge bis 3 MB
- **Sicherung**: automatisch über die Google-Sicherung von Android (Seiten, Vorlagen, Einstellungen) und optional als komplette ZIP mit Anhängen in einen Ordner deiner Wahl, z. B. Google Drive
- **Updates in der App**: Einstellungen → Updates (lädt die neueste APK aus den GitHub-Releases)

## APK herunterladen
Jeder Push baut automatisch eine APK – zu finden unter **Releases** bzw. im Actions-Lauf als Artifact.

## Signatur
Standardmäßig wird mit dem Schlüssel in `signing/` signiert, damit Updates sich über die alte Version installieren lassen.
Für einen privaten Schlüssel die Secrets `VOID_KEYSTORE_BASE64`, `VOID_KEYSTORE_PASSWORD`, `VOID_KEY_ALIAS` und `VOID_KEY_PASSWORD` im Repository hinterlegen.

## Teilen einrichten (einmalig)
1. [Firebase-Konsole](https://console.firebase.google.com) → Projekt hinzufügen.
2. Realtime Database → Datenbank erstellen (z. B. europe-west1) → „gesperrter Modus“.
3. Reiter „Regeln“ ersetzen durch:
   ```json
   {
     "rules": {
       ".read": false,
       ".write": false,
       "notes": { "$id": { ".read": "$id.length >= 20", ".write": "$id.length >= 20" } }
     }
   }
   ```
4. Datenbank-Adresse (`https://…firebasedatabase.app`) in der App unter Einstellungen → Teilen eintragen – oder im Repo als Actions-Variable `VOID_FIREBASE_URL` hinterlegen, dann ist sie in jeder APK eingebaut.
5. **GitHub Pages einschalten** (sonst zeigen Links eine 404-Seite): Repo → Settings → Pages → Source „Deploy from a branch“ → Branch `ccr-cb163825-s6okw8`, Ordner `/docs` → Save.
6. Android 12+: in der App unter Einstellungen → Teilen „Links in App öffnen erlauben“ → `gun642.github.io` anhaken – dann öffnen Links direkt die App.

Wer einen Link bekommt, braucht nichts einzurichten – die Datenbank-Adresse steckt im Link. Der Link öffnet die Notiz auch direkt im Browser (lesen und bearbeiten, live, ohne App).
