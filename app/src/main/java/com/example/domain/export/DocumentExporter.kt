package com.example.domain.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.local.TodoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface ExportTarget {
    data class Single(val todo: TodoEntity) : ExportTarget
    data class All(val todos: List<TodoEntity>, val filterLabel: String) : ExportTarget

    fun items(): List<TodoEntity> = when (this) {
        is Single -> listOf(todo)
        is All -> todos
    }

    fun documentTitle(): String = when (this) {
        is Single -> todo.title.ifBlank { "Untitled Note" }
        is All -> "Folio Tasks — $filterLabel"
    }
}

class DocumentExporter {

    private val dateFormatter = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    private val fileDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun buildSuggestedFileName(target: ExportTarget, format: ExportFormat): String {
        val rawBase = when (target) {
            is ExportTarget.Single -> target.todo.title.ifBlank { "folio_note" }
            is ExportTarget.All -> "folio_tasks_${fileDateFormatter.format(Date())}"
        }
        val sanitized = rawBase
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "folio_note" }
        return "$sanitized.${format.extension}"
    }

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        target: ExportTarget,
        format: ExportFormat
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open destination file")
            outputStream.use { stream ->
                writeDocument(target.items(), target.documentTitle(), format, stream)
            }
        }
    }

    suspend fun exportToCacheFile(
        context: Context,
        target: ExportTarget,
        format: ExportFormat
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, buildSuggestedFileName(target, format))
            FileOutputStream(file).use { stream ->
                writeDocument(target.items(), target.documentTitle(), format, stream)
            }
            file
        }
    }

    fun writeDocument(
        todos: List<TodoEntity>,
        documentTitle: String,
        format: ExportFormat,
        outputStream: OutputStream
    ) {
        when (format) {
            ExportFormat.TXT -> writeTxt(todos, documentTitle, outputStream)
            ExportFormat.DOCX -> writeDocx(todos, documentTitle, outputStream)
            ExportFormat.PDF -> writePdf(todos, documentTitle, outputStream)
        }
    }

    fun writeTxt(
        todos: List<TodoEntity>,
        documentTitle: String,
        outputStream: OutputStream
    ) {
        val sb = StringBuilder()
        sb.appendLine(documentTitle)
        sb.appendLine("=".repeat(documentTitle.length.coerceIn(12, 48)))
        sb.appendLine("Exported: ${dateFormatter.format(Date())}")
        sb.appendLine()

        todos.forEachIndexed { index, todo ->
            val check = if (todo.isCompleted) "[x]" else "[ ]"
            sb.appendLine("$check ${todo.title}")
            sb.appendLine("    Priority: ${todo.priority.label} | Updated: ${dateFormatter.format(Date(todo.updatedAt))}")
            if (todo.note.isNotBlank()) {
                sb.appendLine()
                todo.note.lines().forEach { line ->
                    sb.appendLine("    $line")
                }
            }
            if (index < todos.lastIndex) {
                sb.appendLine()
                sb.appendLine("-".repeat(40))
                sb.appendLine()
            }
        }

        outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    fun writeDocx(
        todos: List<TodoEntity>,
        documentTitle: String,
        outputStream: OutputStream
    ) {
        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val bodyBuilder = StringBuilder()
        bodyBuilder.append(buildDocxParagraph(documentTitle, bold = true, sizeHalfPoints = 36, colorHex = "195A42", spaceAfter = 80))
        bodyBuilder.append(buildDocxParagraph("Exported on ${dateFormatter.format(Date())}", bold = false, sizeHalfPoints = 18, colorHex = "635F57", spaceAfter = 240))

        todos.forEachIndexed { index, todo ->
            val statusPrefix = if (todo.isCompleted) "[DONE] " else "[TODO] "
            bodyBuilder.append(
                buildDocxParagraph(
                    text = "$statusPrefix${todo.title}",
                    bold = true,
                    sizeHalfPoints = 26,
                    colorHex = if (todo.isCompleted) "635F57" else "1C1B18",
                    spaceAfter = 60
                )
            )
            bodyBuilder.append(
                buildDocxParagraph(
                    text = "Priority: ${todo.priority.label}  •  Updated: ${dateFormatter.format(Date(todo.updatedAt))}",
                    bold = false,
                    sizeHalfPoints = 18,
                    colorHex = "635F57",
                    spaceAfter = 120
                )
            )
            if (todo.note.isNotBlank()) {
                todo.note.lines().forEach { line ->
                    bodyBuilder.append(
                        buildDocxParagraph(
                            text = line.ifEmpty { " " },
                            bold = false,
                            sizeHalfPoints = 22,
                            colorHex = "1C1B18",
                            spaceAfter = 80
                        )
                    )
                }
            }
            if (index < todos.lastIndex) {
                bodyBuilder.append(
                    buildDocxParagraph(
                        text = "────────────────────────────────────",
                        bold = false,
                        sizeHalfPoints = 18,
                        colorHex = "CFC9BC",
                        spaceAfter = 180
                    )
                )
            }
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $bodyBuilder
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>"""

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(relsXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun buildDocxParagraph(
        text: String,
        bold: Boolean,
        sizeHalfPoints: Int,
        colorHex: String,
        spaceAfter: Int
    ): String {
        val escaped = escapeXml(text)
        val boldTag = if (bold) "<w:b/>" else ""
        return """
            <w:p>
              <w:pPr>
                <w:spacing w:after="$spaceAfter"/>
              </w:pPr>
              <w:r>
                <w:rPr>
                  $boldTag
                  <w:color w:val="$colorHex"/>
                  <w:sz w:val="$sizeHalfPoints"/>
                </w:rPr>
                <w:t xml:space="preserve">$escaped</w:t>
              </w:r>
            </w:p>
        """.trimIndent()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\t') sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun writePdf(
        todos: List<TodoEntity>,
        documentTitle: String,
        outputStream: OutputStream
    ) {
        val nativeSuccess = try {
            writeNativePdf(todos, documentTitle, outputStream)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: UnsatisfiedLinkError) {
            false
        }

        if (!nativeSuccess) {
            writePureKotlinPdf(todos, documentTitle, outputStream)
        }
    }

    private fun writeNativePdf(
        todos: List<TodoEntity>,
        documentTitle: String,
        outputStream: OutputStream
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44f
        val contentWidth = pageWidth - margin * 2
        val bottomLimit = pageHeight - margin

        val pdf = PdfDocument()
        try {
            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = margin + 12f

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#195A42")
                textSize = 20f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#635F57")
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E5DFD2")
                strokeWidth = 1f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1C1B18")
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val completedTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#635F57")
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#635F57")
                textSize = 10f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1C1B18")
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            fun ensureSpace(neededHeight: Float) {
                if (y + neededHeight > bottomLimit) {
                    pdf.finishPage(page)
                    pageNumber++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    y = margin + 12f
                }
            }

            fun drawWrappedLines(text: String, paint: Paint, x: Float, maxWidth: Float, lineHeight: Float) {
                val paragraphs = text.lines()
                for (paragraph in paragraphs) {
                    if (paragraph.isEmpty()) {
                        ensureSpace(lineHeight)
                        y += lineHeight * 0.7f
                        continue
                    }
                    var start = 0
                    while (start < paragraph.length) {
                        var count = paint.breakText(paragraph, start, paragraph.length, true, maxWidth, null)
                        if (count <= 0) count = 1
                        var end = start + count
                        if (end < paragraph.length) {
                            val lastSpace = paragraph.lastIndexOf(' ', end - 1)
                            if (lastSpace > start) {
                                end = lastSpace + 1
                            }
                        }
                        ensureSpace(lineHeight)
                        val slice = paragraph.substring(start, end).trimEnd()
                        canvas.drawText(slice, x, y, paint)
                        y += lineHeight
                        start = end
                        while (start < paragraph.length && paragraph[start] == ' ') start++
                    }
                }
            }

            drawWrappedLines(documentTitle, headerPaint, margin, contentWidth, 26f)
            drawWrappedLines("Exported ${dateFormatter.format(Date())}", subHeaderPaint, margin, contentWidth, 16f)
            y += 4f
            canvas.drawLine(margin, y, pageWidth - margin, y, rulePaint)
            y += 20f

            todos.forEachIndexed { index, todo ->
                val statusLabel = if (todo.isCompleted) "[DONE]" else "[TODO]"
                val activeTitlePaint = if (todo.isCompleted) completedTitlePaint else titlePaint
                drawWrappedLines("$statusLabel  ${todo.title}", activeTitlePaint, margin, contentWidth, 19f)
                drawWrappedLines(
                    "Priority: ${todo.priority.label}  •  Updated: ${dateFormatter.format(Date(todo.updatedAt))}",
                    metaPaint,
                    margin,
                    contentWidth,
                    15f
                )
                if (todo.note.isNotBlank()) {
                    y += 4f
                    drawWrappedLines(todo.note, bodyPaint, margin + 8f, contentWidth - 8f, 16.5f)
                }
                if (index < todos.lastIndex) {
                    y += 8f
                    ensureSpace(18f)
                    canvas.drawLine(margin, y, pageWidth - margin, y, rulePaint)
                    y += 18f
                }
            }

            pdf.finishPage(page)
            pdf.writeTo(outputStream)
            outputStream.flush()
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun writePureKotlinPdf(
        todos: List<TodoEntity>,
        documentTitle: String,
        outputStream: OutputStream
    ) {
        data class PdfLine(val text: String, val fontAlias: String, val fontSize: Int, val leading: Int)

        fun wrapText(text: String, maxChars: Int): List<String> {
            val result = mutableListOf<String>()
            for (paragraph in text.lines()) {
                if (paragraph.isEmpty()) {
                    result.add("")
                    continue
                }
                var remaining = paragraph
                while (remaining.length > maxChars) {
                    val splitAt = remaining.lastIndexOf(' ', maxChars).let { if (it > 0) it else maxChars }
                    result.add(remaining.substring(0, splitAt).trimEnd())
                    remaining = remaining.substring(splitAt).trimStart()
                }
                if (remaining.isNotEmpty()) {
                    result.add(remaining)
                }
            }
            return result
        }

        val lines = mutableListOf<PdfLine>()
        wrapText(documentTitle, 52).forEach { lines.add(PdfLine(it, "F2", 18, 24)) }
        lines.add(PdfLine("Exported ${dateFormatter.format(Date())}", "F1", 10, 18))
        lines.add(PdfLine("------------------------------------------------------------", "F1", 10, 16))

        todos.forEachIndexed { index, todo ->
            val status = if (todo.isCompleted) "[DONE]" else "[TODO]"
            wrapText("$status ${todo.title}", 64).forEach { lines.add(PdfLine(it, "F2", 13, 18)) }
            lines.add(
                PdfLine(
                    "Priority: ${todo.priority.label} | Updated: ${dateFormatter.format(Date(todo.updatedAt))}",
                    "F1",
                    10,
                    15
                )
            )
            if (todo.note.isNotBlank()) {
                wrapText(todo.note, 72).forEach { lines.add(PdfLine("  $it", "F1", 11, 16)) }
            }
            if (index < todos.lastIndex) {
                lines.add(PdfLine("", "F1", 10, 12))
            }
        }

        val pagesContent = mutableListOf<String>()
        var currentStream = StringBuilder()
        var y = 780
        for (line in lines) {
            if (y < 60) {
                pagesContent.add(currentStream.toString())
                currentStream = StringBuilder()
                y = 780
            }
            val safeText = escapePdfLiteral(line.text)
            currentStream.append("BT /${line.fontAlias} ${line.fontSize} Tf 48 $y Td ($safeText) Tj ET\n")
            y -= line.leading
        }
        pagesContent.add(currentStream.toString())

        val objects = mutableListOf<String>()
        val pageCount = pagesContent.size
        val firstPageObjId = 5
        val kidsRefs = (0 until pageCount).joinToString(" ") { "${firstPageObjId + it * 2} 0 R" }

        objects.add("<< /Type /Catalog /Pages 2 0 R >>")
        objects.add("<< /Type /Pages /Kids [$kidsRefs] /Count $pageCount >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")

        pagesContent.forEachIndexed { index, content ->
            val contentObjId = firstPageObjId + index * 2 + 1
            objects.add(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $contentObjId 0 R >>"
            )
            val contentBytes = content.toByteArray(Charsets.ISO_8859_1)
            objects.add("<< /Length ${contentBytes.size} >>\nstream\n$content\nendstream")
        }

        val buffer = java.io.ByteArrayOutputStream()
        fun writeAscii(text: String) = buffer.write(text.toByteArray(Charsets.ISO_8859_1))

        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        objects.forEachIndexed { idx, objBody ->
            offsets.add(buffer.size())
            writeAscii("${idx + 1} 0 obj\n$objBody\nendobj\n")
        }

        val xrefOffset = buffer.size()
        writeAscii("xref\n0 ${objects.size + 1}\n")
        writeAscii("0000000000 65535 f \n")
        for (i in 1..objects.size) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offsets[i]))
        }
        writeAscii("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF")

        outputStream.write(buffer.toByteArray())
        outputStream.flush()
    }

    private fun escapePdfLiteral(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '\r', '\n' -> sb.append(' ')
                else -> sb.append(if (ch.code in 32..126) ch else '?')
            }
        }
        return sb.toString()
    }
}
