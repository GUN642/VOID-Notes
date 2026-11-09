package app.voidnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voidnotes.data.Page
import app.voidnotes.ui.theme.VoidTheme
import app.voidnotes.ui.theme.headingStyle
import java.text.DateFormat
import java.util.Date

private val shortDate = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
fun formatDate(ms: Long): String = if (ms <= 0) "—" else shortDate.format(Date(ms))

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024; i++
    }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}

/** "gerade eben", "vor 5 Min.", "gestern" … */
fun relativeTime(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        ms <= 0 -> "—"
        d < 60_000 -> "gerade eben"
        d < 3_600_000 -> "vor ${d / 60_000} Min."
        d < 86_400_000 -> "vor ${d / 3_600_000} Std."
        d < 2 * 86_400_000 -> "gestern"
        d < 7 * 86_400_000L -> "vor ${d / 86_400_000} Tagen"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    }
}

/** Nothing-style dot grid backdrop. */
@Composable
fun DotGrid(modifier: Modifier = Modifier, spacing: Dp = 18.dp, radius: Dp = 1.2.dp) {
    val color = VoidTheme.colors.dot
    if (!VoidTheme.dotGrid) return
    Canvas(modifier) {
        val step = spacing.toPx()
        val r = radius.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(color, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

/** Small uppercase mono label, Nothing style. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = VoidTheme.colors.textMuted) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Pill-shaped chip used for toggles in headers and dialogs. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = VoidTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.text else Color.Transparent)
            .border(1.2.dp, if (selected) c.text else c.textMuted.copy(alpha = 0.55f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = if (selected) c.background else c.text)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            color = if (selected) c.background else c.text,
            maxLines = 1,
        )
    }
}

/** Round icon button with a soft background, like the top bar of the screenshot. */
@Composable
fun RoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    tint: Color? = null,
) {
    val c = VoidTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    filled -> c.text
                    hovered -> c.surfaceHigh
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(size * 0.48f), tint = tint ?: if (filled) c.background else c.text)
    }
}

/** Page icon: the emoji, or a plain document glyph. */
@Composable
fun PageIcon(page: Page?, size: Dp, modifier: Modifier = Modifier) {
    val c = VoidTheme.colors
    val icon = page?.icon.orEmpty()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (icon.isNotBlank()) {
            Text(icon, fontSize = (size.value * 0.78f).sp, lineHeight = (size.value * 0.9f).sp, textAlign = TextAlign.Center, maxLines = 1)
        } else {
            Icon(Icons.Outlined.Description, null, Modifier.size(size * 0.85f), tint = c.textMuted)
        }
    }
}

@Composable
fun VoidDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String?,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    dismissText: String = "Abbrechen",
    content: @Composable () -> Unit,
) {
    val c = VoidTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title.uppercase(), style = headingStyle(24), color = c.text) },
        text = { content() },
        confirmButton = {
            if (confirmText != null) {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(confirmText.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (confirmEnabled) c.accent else c.textMuted)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText.uppercase(), style = MaterialTheme.typography.labelLarge, color = c.textMuted)
            }
        },
    )
}

/** An entry of a [VoidMenu]. */
data class MenuItem(val label: String, val icon: ImageVector?, val danger: Boolean = false, val enabled: Boolean = true, val action: () -> Unit)

/** Divider line in a menu (also usable inside listOfNotNull). */
val Separator = MenuItem("", null) {}

@Composable
fun VoidMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem?>, offset: DpOffset = DpOffset(0.dp, 0.dp)) {
    val c = VoidTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        containerColor = c.surfaceHigh,
        shape = RoundedCornerShape(16.dp),
    ) {
        items.forEach { item ->
            if (item == null || item === Separator) {
                Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(c.divider))
                return@forEach
            }
            DropdownMenuItem(
                text = {
                    Text(
                        item.label,
                        color = when {
                            !item.enabled -> c.textMuted
                            item.danger -> c.accent
                            else -> c.text
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = item.icon?.let { ic -> { Icon(ic, null, Modifier.size(20.dp), tint = if (item.danger) c.accent else c.textMuted) } },
                enabled = item.enabled,
                onClick = {
                    onDismiss()
                    item.action()
                },
            )
        }
    }
}

/** Header of a collapsible home section ("Favoriten", "Privat" …). */
@Composable
fun SectionHeader(
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val c = VoidTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle).padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(8.dp))
        Label(title, color = c.text)
        Spacer(Modifier.width(6.dp))
        Text(if (collapsed) "›" else "⌄", color = c.accent, fontSize = 13.sp, lineHeight = 13.sp)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** A large dot-matrix headline with the red period. */
@Composable
fun Headline(text: String, modifier: Modifier = Modifier, size: Int = 34) {
    val c = VoidTheme.colors
    Row(modifier) {
        Text(text, style = headingStyle(size), color = c.text, maxLines = 1)
        Text(".", style = headingStyle(size), color = c.accent)
    }
}

/** Empty state with a ring of dots. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    val c = VoidTheme.colors
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DotRing(Modifier.size(64.dp))
        Text(title.uppercase(), style = headingStyle(20), color = c.text, textAlign = TextAlign.Center)
        Text(text, color = c.textMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun DotRing(modifier: Modifier = Modifier, dots: Int = 20) {
    val c = VoidTheme.colors
    Canvas(modifier) {
        val radius = minOf(size.width, size.height) / 2f
        val dotR = radius * 0.08f
        val ringR = radius - dotR
        for (i in 0 until dots) {
            val a = -Math.PI / 2 + 2 * Math.PI * i / dots
            drawCircle(
                if (i == 0) c.accent else c.textMuted.copy(alpha = 0.3f),
                dotR,
                Offset(center.x + (ringR * Math.cos(a)).toFloat(), center.y + (ringR * Math.sin(a)).toFloat()),
            )
        }
    }
}
