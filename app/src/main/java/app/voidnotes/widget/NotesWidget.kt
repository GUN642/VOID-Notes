package app.voidnotes.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.voidnotes.MainActivity
import app.voidnotes.data.Accent
import app.voidnotes.data.Page
import app.voidnotes.data.SettingsRepository
import app.voidnotes.data.Store
import app.voidnotes.data.ThemeMode

enum class WidgetContent(val label: String, val header: String) {
    FAVORITES("Favoriten", "Favoriten"),
    RECENT("Zuletzt verwendet", "Zuletzt"),
}

/** Per-widget choice, stored in shared preferences. */
data class WidgetConfig(val content: WidgetContent = WidgetContent.FAVORITES, val newButton: Boolean = true) {
    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        fun load(context: Context, id: Int): WidgetConfig {
            val p = prefs(context)
            return WidgetConfig(
                content = p.getString("content_$id", null)?.let { v -> WidgetContent.entries.firstOrNull { it.name == v } } ?: WidgetContent.FAVORITES,
                newButton = p.getBoolean("new_$id", true),
            )
        }

        fun save(context: Context, id: Int, c: WidgetConfig) {
            prefs(context).edit().putString("content_$id", c.content.name).putBoolean("new_$id", c.newButton).apply()
        }
    }
}

const val ACTION_OPEN_PAGE = "app.voidnotes.OPEN_PAGE"
const val ACTION_NEW_NOTE = "app.voidnotes.NEW_NOTE"
const val EXTRA_PAGE = "page"

private class Palette(val bg: Color, val surface: Color, val text: Color, val muted: Color, val accent: Color, val onAccent: Color)

class NotesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfig.load(context, appWidgetId)
        val store = Store.get(context)
        val settings = SettingsRepository(context).settings.value
        val nightMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = when (settings.theme) {
            ThemeMode.BLACK, ThemeMode.GRAPHITE -> true
            ThemeMode.PAPER, ThemeMode.WHITE -> false
            ThemeMode.SYSTEM -> nightMode
        }
        var accent = Color(settings.accent.argb)
        if (settings.accent == Accent.MONO) accent = if (dark) Color.White else Color(0xFF111111)
        val onAccent = if (settings.accent == Accent.MONO && dark || settings.accent == Accent.YELLOW) Color.Black else Color.White
        val palette = if (dark) {
            Palette(if (settings.theme == ThemeMode.GRAPHITE) Color(0xFF1C1C1E) else Color.Black, Color(0xFF1C1C1C), Color.White, Color(0xFFADADAD), accent, onAccent)
        } else {
            Palette(if (settings.theme == ThemeMode.PAPER) Color(0xFFEFEEEA) else Color.White, Color(0xFFF2F2F2).takeIf { settings.theme != ThemeMode.PAPER } ?: Color.White,
                Color(0xFF0A0A0A), Color(0xFF4F4F4F), accent, onAccent)
        }
        provideContent {
            val pages by store.pages.collectAsState()
            WidgetBody(config, pages, palette)
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { NotesWidget().updateAll(context) }
        }
    }
}

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}

private fun openIntent(context: Context, page: Page) = Intent(context, MainActivity::class.java)
    .setAction(ACTION_OPEN_PAGE)
    .setData(Uri.parse("voidnotes-widget://page/${page.id}"))
    .putExtra(EXTRA_PAGE, page.id)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun newIntent(context: Context) = Intent(context, MainActivity::class.java)
    .setAction(ACTION_NEW_NOTE)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun homeIntent(context: Context) = Intent(context, MainActivity::class.java)
    .setAction(Intent.ACTION_MAIN)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun WidgetBody(config: WidgetConfig, pages: Map<String, Page>, p: Palette) {
    val context = LocalContext.current
    val size = LocalSize.current
    val live = pages.values.filterNot { it.inTrash }
    val list = when (config.content) {
        WidgetContent.FAVORITES -> live.filter { it.favorite }.sortedBy { it.displayTitle.lowercase() }
        WidgetContent.RECENT -> live.filter { it.opened > 0 || it.updated > 0 }.sortedByDescending { maxOf(it.opened, it.updated) }.take(20)
    }
    // Scale with the widget: bigger text and rows on large widgets, two columns when wide.
    val big = size.width >= 260.dp && size.height >= 160.dp
    val tiny = size.height < 100.dp
    val columns = if (size.width >= 320.dp && size.height >= 150.dp) 2 else 1
    val titleSize: TextUnit = if (big) 16.sp else 14.sp
    val rowHeight: Dp = if (big) 48.dp else 40.dp
    val pad = if (tiny) 8.dp else 12.dp

    Column(
        GlanceModifier.fillMaxSize().background(ColorProvider(p.bg)).cornerRadius(24.dp).padding(pad),
    ) {
        // Header: logo, what is shown, new note
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.defaultWeight().clickable(actionStartActivity(homeIntent(context))), verticalAlignment = Alignment.CenterVertically) {
                Text("VOID", style = TextStyle(color = ColorProvider(p.text), fontSize = if (big) 20.sp else 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                Text(".", style = TextStyle(color = ColorProvider(p.accent), fontSize = if (big) 20.sp else 17.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                if (size.width >= 150.dp) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        config.content.header.uppercase(),
                        style = TextStyle(color = ColorProvider(p.muted), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                    )
                }
            }
            if (config.newButton) {
                Box(
                    GlanceModifier.size(if (tiny) 32.dp else 38.dp).cornerRadius(19.dp).background(ColorProvider(p.accent))
                        .clickable(actionStartActivity(newIntent(context))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = TextStyle(color = ColorProvider(p.onAccent), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        if (tiny) {
            // Very flat widget: the first entry only.
            list.firstOrNull()?.let { first ->
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    (first.icon + " " + first.displayTitle).trim(),
                    style = TextStyle(color = ColorProvider(p.text), fontSize = 13.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.clickable(actionStartActivity(openIntent(context, first))),
                )
            }
            return@Column
        }
        Spacer(GlanceModifier.height(8.dp))
        if (list.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (config.content == WidgetContent.FAVORITES) "Noch keine Favoriten – eine Seite mit ☆ markieren" else "Noch keine Seiten",
                    style = TextStyle(color = ColorProvider(p.muted), fontSize = 13.sp),
                )
            }
            return@Column
        }
        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(list.chunked(columns), itemId = { row -> row.first().id.hashCode().toLong() }) { row ->
                Column {
                    Row(GlanceModifier.fillMaxWidth()) {
                        row.forEachIndexed { i, page ->
                            if (i > 0) Spacer(GlanceModifier.width(6.dp))
                            Row(
                                GlanceModifier.defaultWeight().height(rowHeight).cornerRadius(14.dp).background(ColorProvider(p.surface))
                                    .padding(horizontal = 10.dp)
                                    .clickable(actionStartActivity(openIntent(context, page))),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    page.icon.ifBlank { "•" },
                                    style = TextStyle(color = ColorProvider(if (page.icon.isBlank()) p.accent else p.text), fontSize = titleSize),
                                )
                                Spacer(GlanceModifier.width(8.dp))
                                Text(
                                    page.displayTitle,
                                    style = TextStyle(color = ColorProvider(p.text), fontSize = titleSize, fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                )
                            }
                        }
                        if (row.size < columns) {
                            Spacer(GlanceModifier.width(6.dp))
                            Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }
}
