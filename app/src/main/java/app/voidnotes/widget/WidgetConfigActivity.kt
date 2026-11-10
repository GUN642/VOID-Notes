package app.voidnotes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import app.voidnotes.data.SettingsRepository
import app.voidnotes.ui.DotGrid
import app.voidnotes.ui.Headline
import app.voidnotes.ui.Label
import app.voidnotes.ui.theme.VoidNotesTheme
import app.voidnotes.ui.theme.VoidTheme
import kotlinx.coroutines.launch

/** Shown when the widget is placed (and later via "Widget bearbeiten"): what it shows. */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }
        val settings = SettingsRepository(this).settings.value
        val initial = WidgetConfig.load(this, widgetId)
        setContent {
            VoidNotesTheme(settings.theme, settings.accent, settings.dotHeadings, settings.dotGrid, settings.font) {
                val c = VoidTheme.colors
                val scope = rememberCoroutineScope()
                var content by remember { mutableStateOf(initial.content) }
                var newButton by remember { mutableStateOf(initial.newButton) }
                var opacity by remember { mutableStateOf(initial.opacity.toFloat()) }
                Box(Modifier.fillMaxSize().background(c.background)) {
                    DotGrid(Modifier.fillMaxSize(), spacing = 22.dp)
                    Column(
                        Modifier.safeDrawingPadding().padding(24.dp).widthIn(max = 560.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Headline("Widget", size = 40)
                        Label("VOID Notes auf dem Startbildschirm")
                        Spacer(Modifier.height(8.dp))
                        Label("Inhalt", color = c.text)
                        WidgetContent.entries.forEach { option ->
                            val selected = content == option
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
                                    .border(if (selected) 1.5.dp else 1.dp, if (selected) c.accent else c.divider, RoundedCornerShape(18.dp))
                                    .clickable { content = option }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, color = c.text, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (option == WidgetContent.FAVORITES) "Seiten mit Stern ☆" else "Zuletzt geöffnete und bearbeitete Seiten",
                                        color = c.textMuted, style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) c.accent else c.textMuted, CircleShape)
                                        .padding(5.dp).clip(CircleShape).background(if (selected) c.accent else Color.Transparent),
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
                                .border(1.dp, c.divider, RoundedCornerShape(18.dp)).clickable { newButton = !newButton }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Knopf „Neue Notiz“", color = c.text, style = MaterialTheme.typography.bodyLarge)
                                Text("Runder + Knopf oben rechts", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = newButton, onCheckedChange = { newButton = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
                                    uncheckedThumbColor = c.textMuted, uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.divider,
                                ),
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
                                .border(1.dp, c.divider, RoundedCornerShape(18.dp)).padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Hintergrund", color = c.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Label("${opacity.toInt()} % deckend", color = c.text)
                            }
                            Text("0 % = durchsichtig, die Einträge bleiben lesbar", color = c.textMuted, style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.material3.Slider(
                                value = opacity, onValueChange = { opacity = (it / 5f).toInt() * 5f }, valueRange = 0f..100f,
                                colors = androidx.compose.material3.SliderDefaults.colors(
                                    thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surfaceHigh,
                                ),
                            )
                        }
                        Label("Die Größe passt sich an: flach nur Kopfzeile, groß als Liste, breit in zwei Spalten.")
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.text)
                                .clickable {
                                    WidgetConfig.save(this@WidgetConfigActivity, widgetId, WidgetConfig(content, newButton, opacity.toInt()))
                                    scope.launch {
                                        runCatching {
                                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId)
                                            NotesWidget().update(this@WidgetConfigActivity, glanceId)
                                        }
                                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                        finish()
                                    }
                                }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("FERTIG", style = MaterialTheme.typography.labelLarge, color = c.background)
                        }
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
        }
    }
}
