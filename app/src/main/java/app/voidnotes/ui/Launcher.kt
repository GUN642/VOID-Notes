package app.voidnotes.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import app.voidnotes.data.PdfPrintAdapter
import java.io.File

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

const val LINK_HOST = "gun642.github.io"

/** Everything that hands work to Android or other apps. */
class Launcher(private val context: Context) {
    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Keine passende App gefunden")
        } catch (e: SecurityException) {
            toast("Nicht erlaubt: ${e.message}")
        }
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** Opens an attachment in a fitting app. */
    fun open(file: File, mime: String, name: String) {
        if (!file.exists()) return toast("Datei nicht gefunden")
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(file), mime.ifBlank { "*/*" })
            .putExtra(Intent.EXTRA_TITLE, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(intent, "Öffnen mit"))
    }

    fun share(file: File, mime: String, subject: String) {
        if (!file.exists()) return toast("Datei nicht gefunden")
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uriFor(file))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(Intent.createChooser(intent, "Teilen"))
    }

    fun shareText(subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        start(Intent.createChooser(intent, "Teilen"))
    }

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    /**
     * Whether share links (gun642.github.io) open straight in the app. Android 12+ asks the user to allow this
     * once in the app settings; older versions always offer the app. Null = cannot be checked.
     */
    fun linksApproved(): Boolean? {
        if (android.os.Build.VERSION.SDK_INT < 31) return true
        return runCatching {
            val m = context.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java)
            val state = m.getDomainVerificationUserState(context.packageName) ?: return null
            val s = state.hostToStateMap[LINK_HOST]
            s == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                s == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED
        }.getOrNull()
    }

    /** "Open by default" page of this app, where the link domain can be allowed. */
    fun openLinkSettings() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        start(intent)
    }

    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))

    /** System print dialog for a rendered PDF (also offers "Als PDF speichern"). */
    fun print(pdf: File, jobName: String, pages: Int) {
        val activity = context.findActivity() ?: return toast("Drucken ist hier nicht möglich")
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return toast("Drucken wird nicht unterstützt")
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        pm.print("VOID Notes – $jobName", PdfPrintAdapter(pdf, jobName, pages), attrs)
    }

    /** Hands a downloaded update APK to the system installer. */
    fun installApk(apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            toast("Bitte \"Unbekannte Apps installieren\" für VOID Notes erlauben und dann erneut auf Aktualisieren tippen")
            start(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return
        }
        start(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(apk), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
