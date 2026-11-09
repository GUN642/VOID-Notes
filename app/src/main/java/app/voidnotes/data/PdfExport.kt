package app.voidnotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

data class PdfOptions(val includeIcon: Boolean = true, val pageNumbers: Boolean = true, val accent: Int = 0xFFD71921.toInt())

/** Renders a page as an A4 PDF (used for export, sharing and printing). */
class PdfWriter(private val context: Context, private val store: Store, private val options: PdfOptions) {
    private val pageW = 595
    private val pageH = 842
    private val margin = 54f
    private val contentW get() = pageW - 2 * margin
    private val bottom get() = pageH - margin - 18f

    private val ink = Color.rgb(17, 17, 17)
    private val muted = Color.rgb(120, 120, 120)
    private val line = Color.rgb(224, 224, 224)
    private val soft = Color.rgb(244, 244, 242)

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNo = 0
    private var title = ""

    fun render(p: Page, pages: Map<String, Page>, out: OutputStream): Int {
        doc = PdfDocument()
        title = p.displayTitle
        try {
            newPage()
            // Title
            val titleText = if (options.includeIcon && p.icon.isNotBlank()) "${p.icon}  ${p.displayTitle}" else p.displayTitle
            drawText(SpannableString(titleText), paint(26f, bold = true), 0f)
            y += 4f
            val date = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(p.updated))
            drawText(SpannableString("Zuletzt bearbeitet: $date"), paint(9f, color = muted), 0f)
            y += 10f
            hr()
            y += 8f

            val numbers = numbering(p.blocks)
            var prev: BlockType? = null
            p.blocks.forEachIndexed { i, b ->
                val indent = b.level * 18f
                val listy = b.type in setOf(BlockType.BULLET, BlockType.NUMBERED, BlockType.TODO, BlockType.TOGGLE)
                if (!(listy && prev == b.type)) y += when (b.type) {
                    BlockType.H1 -> 14f
                    BlockType.H2 -> 10f
                    BlockType.H3 -> 6f
                    else -> 3f
                }
                when (b.type) {
                    BlockType.TEXT -> drawText(styled(b.text), paint(11f), indent)
                    BlockType.H1 -> { keep(40f); drawText(styled(b.text), paint(20f, bold = true), indent) }
                    BlockType.H2 -> { keep(36f); drawText(styled(b.text), paint(16f, bold = true), indent) }
                    BlockType.H3 -> { keep(30f); drawText(styled(b.text), paint(13f, bold = true), indent) }
                    BlockType.BULLET -> marker(styled(b.text), indent) { c, cx, cy -> c.drawCircle(cx + 4f, cy, 2.2f, fill(ink)) }
                    BlockType.NUMBERED -> marker(styled(b.text), indent) { c, cx, cy ->
                        val tp = paint(11f)
                        c.drawText("${numbers[i]}.", cx, cy + 4f, tp)
                    }
                    BlockType.TODO -> {
                        val text = styled(b.text)
                        if (b.checked) text.setSpan(StrikethroughSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        marker(text, indent, color = if (b.checked) muted else ink) { c, cx, cy ->
                            val r = RectF(cx, cy - 5.5f, cx + 11f, cy + 5.5f)
                            if (b.checked) {
                                c.drawRoundRect(r, 3f, 3f, fill(options.accent))
                                val path = Path().apply {
                                    moveTo(cx + 2.5f, cy); lineTo(cx + 4.8f, cy + 2.6f); lineTo(cx + 8.8f, cy - 2.6f)
                                }
                                c.drawPath(path, stroke(Color.WHITE, 1.6f))
                            } else {
                                c.drawRoundRect(r, 3f, 3f, stroke(muted, 1f))
                            }
                        }
                    }
                    BlockType.TOGGLE -> marker(styled(b.text), indent) { c, cx, cy ->
                        val path = Path().apply { moveTo(cx + 1f, cy - 4f); lineTo(cx + 9f, cy - 4f); lineTo(cx + 5f, cy + 3f); close() }
                        c.drawPath(path, fill(ink))
                    }
                    BlockType.QUOTE -> boxed(styled(b.text), paint(11.5f, italic = true), indent, bar = ink, bg = null)
                    BlockType.CALLOUT -> boxed(SpannableString("${b.emoji ?: "💡"}  ").let { e ->
                        val s = styled(b.text)
                        SpannableString(android.text.TextUtils.concat(e, s))
                    }, paint(11f), indent, bar = null, bg = soft)
                    BlockType.CODE -> boxed(SpannableString(b.text), paint(9.5f, mono = true), indent, bar = null, bg = soft)
                    BlockType.DIVIDER -> { y += 6f; hr(); y += 6f }
                    BlockType.IMAGE -> image(b, indent)
                    BlockType.FILE -> drawText(SpannableString("📎  ${b.fileName ?: "Datei"}  ·  ${sizeText(b.size)}"), paint(10.5f, color = muted), indent)
                    BlockType.PAGE -> {
                        val sub = pages[b.pageId]
                        val text = SpannableString("↳  ${sub?.let { (it.icon + "  " + it.displayTitle).trim() } ?: "Seite"}")
                        text.setSpan(UnderlineSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        drawText(text, paint(11f), indent)
                    }
                }
                prev = b.type
            }
            finishPage()
            doc.writeTo(out)
            return pageNo
        } finally {
            doc.close()
        }
    }

    // ------------------------------------------------------------------ pages

    private fun newPage() {
        pageNo++
        val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()
        page = doc.startPage(info)
        canvas = page!!.canvas
        y = margin
    }

    private fun finishPage() {
        val c = canvas ?: return
        if (options.pageNumbers) {
            val p = paint(8.5f, color = muted)
            c.drawText(title.take(80), margin, pageH - margin + 14f, p)
            val n = "$pageNo"
            c.drawText(n, pageW - margin - p.measureText(n), pageH - margin + 14f, p)
        }
        doc.finishPage(page)
        page = null; canvas = null
    }

    private fun breakPage() {
        finishPage(); newPage()
    }

    /** Starts a new page when less than [space] is left (keeps headings with their text). */
    private fun keep(space: Float) {
        if (y + space > bottom) breakPage()
    }

    // ------------------------------------------------------------------ text

    private fun paint(size: Float, bold: Boolean = false, italic: Boolean = false, mono: Boolean = false, color: Int = ink) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            typeface = Typeface.create(if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF, style)
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun styled(text: String): SpannableString {
        val st = Inline.strip(text)
        val s = SpannableString(st.text)
        st.ranges.forEach { (style, a, b) ->
            val span: Any = when (style) {
                InlineStyle.BOLD -> StyleSpan(Typeface.BOLD)
                InlineStyle.ITALIC -> StyleSpan(Typeface.ITALIC)
                InlineStyle.STRIKE -> StrikethroughSpan()
                InlineStyle.CODE -> TypefaceSpan("monospace")
                InlineStyle.MARK -> BackgroundColorSpan(Color.argb(70, Color.red(options.accent), Color.green(options.accent), Color.blue(options.accent)))
                InlineStyle.LINK -> UnderlineSpan()
            }
            s.setSpan(span, a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (style == InlineStyle.CODE) s.setSpan(BackgroundColorSpan(soft), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (style == InlineStyle.LINK) s.setSpan(ForegroundColorSpan(options.accent), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return s
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(20))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.18f)
            .setIncludePad(false)
            .build()

    /**
     * Draws a layout at the current position, continuing on the next page when it does not fit.
     * [onFirstLine] gets the vertical centre of the first line (for bullets, checkboxes …).
     */
    private fun drawLayout(l: StaticLayout, x: Float, onFirstLine: ((Canvas, Float) -> Unit)? = null, onChunk: ((Canvas, Float, Float) -> Unit)? = null) {
        if (l.text.isEmpty()) {
            y += l.height.coerceAtLeast(12)
            return
        }
        var first = 0
        var firstDrawn = false
        while (first < l.lineCount) {
            var last = first
            val top = l.getLineTop(first).toFloat()
            while (last + 1 < l.lineCount && y + (l.getLineBottom(last + 1) - top) <= bottom) last++
            if (y + (l.getLineBottom(first) - top) > bottom && y > margin + 1f) {
                breakPage(); continue
            }
            val h = l.getLineBottom(last) - top
            val c = canvas!!
            onChunk?.invoke(c, y, h)
            c.save()
            c.clipRect(x - 2f, y, x + l.width + 2f, y + h)
            c.translate(x, y - top)
            l.draw(c)
            c.restore()
            if (!firstDrawn) {
                firstDrawn = true
                val mid = (l.getLineTop(first) + l.getLineBottom(first)) / 2f - top
                onFirstLine?.invoke(c, y + mid)
            }
            y += h
            first = last + 1
            if (first < l.lineCount) breakPage()
        }
    }

    private fun drawText(text: CharSequence, paint: TextPaint, indent: Float) {
        drawLayout(layout(text, paint, contentW - indent), margin + indent)
        y += 3f
    }

    private fun marker(text: SpannableString, indent: Float, color: Int = ink, draw: (Canvas, Float, Float) -> Unit) {
        val gutter = 18f
        val l = layout(text, paint(11f, color = color), contentW - indent - gutter)
        keep(l.getLineBottom(0).toFloat())
        drawLayout(l, margin + indent + gutter, onFirstLine = { c, cy -> draw(c, margin + indent, cy) })
        y += 2f
    }

    private fun boxed(text: CharSequence, paint: TextPaint, indent: Float, bar: Int?, bg: Int?) {
        val pad = 8f
        val x = margin + indent
        val l = layout(text, paint, contentW - indent - 2 * pad - (if (bar != null) 4f else 0f))
        y += 2f
        y += pad
        drawLayout(l, x + pad + (if (bar != null) 4f else 0f), onChunk = { c, top, h ->
            val r = RectF(x, top - pad, x + contentW - indent, top + h + pad)
            if (bg != null) c.drawRoundRect(r, 6f, 6f, fill(bg))
            if (bar != null) c.drawRect(x, top - pad / 2, x + 2.5f, top + h + pad / 2, fill(bar))
        })
        y += pad + 4f
    }

    private fun hr() {
        canvas?.drawLine(margin, y, pageW - margin, y, stroke(line, 0.8f))
    }

    private fun image(b: Block, indent: Float) {
        val f = b.file?.let { store.attachment(it) }
        val bmp = f?.takeIf { it.exists() }?.let { decode(it, 1400) }
        if (bmp == null) {
            drawText(SpannableString("🖼  ${b.fileName ?: "Bild"}"), paint(10.5f, color = muted), indent)
            return
        }
        val maxW = contentW - indent
        val maxH = (bottom - margin) * 0.7f
        var w = maxW
        var h = w * bmp.height / bmp.width
        if (h > maxH) { h = maxH; w = h * bmp.width / bmp.height }
        if (y + h > bottom) breakPage()
        val dst = RectF(margin + indent, y + 4f, margin + indent + w, y + 4f + h)
        canvas?.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        bmp.recycle()
        y += h + 10f
        if (b.text.isNotBlank()) drawText(styled(b.text), paint(9.5f, color = muted), indent)
    }

    private fun sizeText(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }

    companion object {
        /** Decodes an image no larger than [max] pixels on its longest side, upright. */
        fun decode(file: File, max: Int): Bitmap? = runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, o)
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
            val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val rotation = runCatching {
                when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            if (rotation == 0f) bmp else {
                val m = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
            }
        }.getOrNull()
    }
}

/** Hands a finished PDF file to the Android print dialog. */
class PdfPrintAdapter(private val file: File, private val name: String, private val pages: Int) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled(); return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            FileInputStream(file).use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
