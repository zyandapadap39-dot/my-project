package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.example.data.preferences.UserPreferences
import com.example.viewmodel.FishingAnalytics
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates real CSV and formatted multi-page PDF angler reports for FishLog Premium members.
 */
object FishLogExportManager {

    suspend fun exportCatchesAndSessionsToCsv(
        context: Context,
        destinationUri: Uri,
        catches: List<CatchRecord>,
        sessions: List<FishingSession>,
        spots: List<FishingSpot>,
        preferences: UserPreferences
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val sessionTitleById = sessions.associateBy({ it.id }, { it.title })
            val spotById = spots.associateBy { it.id }

            openExportOutputStream(context, destinationUri)?.use { outStream ->
                OutputStreamWriter(outStream, Charsets.UTF_8).use { writer ->
                    val wLabel = preferences.weightUnit.symbol
                    val lLabel = preferences.lengthUnit.symbol

                    writer.write(
                        listOf(
                            "Date & Time",
                            "Species",
                            "Weight ($wLabel)",
                            "Length ($lLabel)",
                            "Location",
                            "Latitude",
                            "Longitude",
                            "Bait / Lure",
                            "Fishing Method",
                            "Water Conditions",
                            "Released",
                            "Journal Session",
                            "Notes"
                        ).joinToString(",") { escapeCsv(it) } + "\n"
                    )

                    catches.sortedByDescending { it.caughtAtEpochMillis }.forEach { record ->
                        val linkedSpot = record.spotId?.let { spotById[it] }
                        val lat = record.latitude ?: linkedSpot?.latitude
                        val lon = record.longitude ?: linkedSpot?.longitude
                        val formattedWeight = String.format(
                            Locale.US,
                            "%.2f",
                            UnitFormatter.displayWeightValue(record.weightKg, preferences.weightUnit)
                        )
                        val formattedLength = String.format(
                            Locale.US,
                            "%.1f",
                            UnitFormatter.displayLengthValue(record.lengthCm, preferences.lengthUnit)
                        )
                        val row = listOf(
                            dateFmt.format(Date(record.caughtAtEpochMillis)),
                            record.species,
                            formattedWeight,
                            formattedLength,
                            record.locationName,
                            lat?.let { String.format(Locale.US, "%.5f", it) } ?: "",
                            lon?.let { String.format(Locale.US, "%.5f", it) } ?: "",
                            record.baitOrLure,
                            record.fishingMethod,
                            record.waterConditions,
                            if (record.released) "Yes" else "No",
                            record.sessionId?.let { sessionTitleById[it] } ?: "",
                            record.notes ?: ""
                        )
                        writer.write(row.joinToString(",") { escapeCsv(it) } + "\n")
                    }
                    writer.flush()
                }
            } ?: error("Unable to open output stream for selected CSV file.")

            catches.size
        }
    }

    suspend fun exportFishingReportToPdf(
        context: Context,
        destinationUri: Uri,
        anglerName: String,
        analytics: FishingAnalytics,
        catches: List<CatchRecord>,
        sessions: List<FishingSession>,
        preferences: UserPreferences
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val sortedCatches = catches.sortedByDescending { it.caughtAtEpochMillis }
            val renderedNativeBytes = runCatching {
                renderNativeAndroidPdfBytes(anglerName, analytics, sortedCatches, sessions, preferences)
            }.getOrNull()

            openExportOutputStream(context, destinationUri)?.use { out ->
                if (renderedNativeBytes != null && renderedNativeBytes.size > 64) {
                    out.write(renderedNativeBytes)
                } else {
                    out.write(
                        buildStandardPdf14Bytes(
                            anglerName = anglerName,
                            analytics = analytics,
                            catches = sortedCatches,
                            sessionsCount = sessions.size,
                            preferences = preferences
                        )
                    )
                }
                out.flush()
            } ?: error("Unable to open PDF output stream.")

            catches.size
        }
    }

    private fun renderNativeAndroidPdfBytes(
        anglerName: String,
        analytics: FishingAnalytics,
        sortedCatches: List<CatchRecord>,
        sessions: List<FishingSession>,
        preferences: UserPreferences
    ): ByteArray {
        val catches = sortedCatches
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)

        try {
            val navyColor = Color.rgb(10, 22, 34)
            val forestColor = Color.rgb(26, 102, 68)
            val electricAccent = Color.rgb(46, 229, 133)
            val slateText = Color.rgb(75, 98, 120)
            val lightCardFill = Color.rgb(242, 246, 249)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = electricAccent
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navyColor
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = navyColor
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = slateText
                textSize = 9.5f
            }
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas: Canvas = page.canvas

            // Page 1 Header Banner
            boxPaint.color = navyColor
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, boxPaint)

            canvas.drawText("FISHLOG PREMIUM — OFFICIAL ANGLER LOGBOOK", 36f, 40f, titlePaint)
            canvas.drawText(
                "Angler: ${anglerName.ifBlank { "Angler" }}  •  Generated ${dateFmt.format(Date())}",
                36f,
                64f,
                subHeaderPaint
            )

            // Summary KPI Box
            var cursorY = 122f
            boxPaint.color = lightCardFill
            canvas.drawRoundRect(RectF(36f, cursorY, (pageWidth - 36).toFloat(), cursorY + 78f), 10f, 10f, boxPaint)

            val biggestStr = analytics.biggestByWeight?.let {
                "${UnitFormatter.formatWeight(it.weightKg, preferences.weightUnit)} (${it.species})"
            } ?: "—"
            val longestStr = analytics.longestFish?.let {
                "${UnitFormatter.formatLength(it.lengthCm, preferences.lengthUnit)} (${it.species})"
            } ?: "—"

            canvas.drawText("Summary Analytics & Personal Records", 50f, cursorY + 22f, sectionPaint)
            canvas.drawText(
                "Total Catches: ${analytics.totalCatches}    •    Journal Sessions: ${sessions.size}    •    Top Species: ${analytics.mostFrequentSpecies ?: "—"}",
                50f,
                cursorY + 44f,
                bodyBoldPaint
            )
            canvas.drawText(
                "Biggest Catch: $biggestStr    •    Longest Fish: $longestStr",
                50f,
                cursorY + 62f,
                bodyPaint
            )

            cursorY += 104f
            canvas.drawText("Recorded Catches (${catches.size})", 36f, cursorY, sectionPaint)
            cursorY += 14f

            // Table Header
            boxPaint.color = forestColor
            canvas.drawRoundRect(RectF(36f, cursorY, (pageWidth - 36).toFloat(), cursorY + 24f), 4f, 4f, boxPaint)
            val tableHeaderPaint = Paint(bodyBoldPaint).apply { color = Color.WHITE }
            canvas.drawText("Date", 44f, cursorY + 16f, tableHeaderPaint)
            canvas.drawText("Species", 120f, cursorY + 16f, tableHeaderPaint)
            canvas.drawText("Size", 245f, cursorY + 16f, tableHeaderPaint)
            canvas.drawText("Location", 345f, cursorY + 16f, tableHeaderPaint)
            canvas.drawText("Bait / Method", 455f, cursorY + 16f, tableHeaderPaint)
            cursorY += 34f

            if (sortedCatches.isEmpty()) {
                canvas.drawText("No catch records logged yet.", 44f, cursorY + 12f, bodyPaint)
            } else {
                sortedCatches.forEachIndexed { index, item ->
                    if (cursorY > pageHeight - 64f) {
                        pdf.finishPage(page)
                        pageNumber++
                        page = pdf.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        )
                        canvas = page.canvas
                        cursorY = 48f
                        canvas.drawText("FishLog Catch Logbook (Page $pageNumber)", 36f, cursorY, sectionPaint)
                        cursorY += 24f
                    }

                    if (index % 2 == 1) {
                        boxPaint.color = lightCardFill
                        canvas.drawRect(36f, cursorY - 12f, (pageWidth - 36).toFloat(), cursorY + 10f, boxPaint)
                    }

                    val sizeText = "${UnitFormatter.formatWeight(item.weightKg, preferences.weightUnit)} / ${
                        UnitFormatter.formatLength(item.lengthCm, preferences.lengthUnit)
                    }"
                    canvas.drawText(dateFmt.format(Date(item.caughtAtEpochMillis)), 44f, cursorY, bodyPaint)
                    canvas.drawText(truncateText(item.species, 20), 120f, cursorY, bodyBoldPaint)
                    canvas.drawText(truncateText(sizeText, 18), 245f, cursorY, bodyPaint)
                    canvas.drawText(truncateText(item.locationName, 18), 345f, cursorY, bodyPaint)
                    canvas.drawText(truncateText("${item.baitOrLure} (${item.fishingMethod})", 20), 455f, cursorY, bodyPaint)
                    cursorY += 22f
                }
            }

            pdf.finishPage(page)
            val outBuf = java.io.ByteArrayOutputStream()
            pdf.writeTo(outBuf)
            return outBuf.toByteArray()
        } finally {
            runCatching { pdf.close() }
        }
    }

    private fun buildStandardPdf14Bytes(
        anglerName: String,
        analytics: FishingAnalytics,
        catches: List<CatchRecord>,
        sessionsCount: Int,
        preferences: UserPreferences
    ): ByteArray {
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val lines = mutableListOf<String>()
        lines.add("FISHLOG PREMIUM - OFFICIAL ANGLER LOGBOOK")
        lines.add("Angler: ${anglerName.ifBlank { "Angler" }} | Generated: ${dateFmt.format(Date())}")
        lines.add("Total Catches: ${analytics.totalCatches} | Sessions: $sessionsCount | Top Species: ${analytics.mostFrequentSpecies ?: "-"}")
        lines.add("------------------------------------------------------------------------")
        catches.take(35).forEach { c ->
            val sizeStr = "${UnitFormatter.formatWeight(c.weightKg, preferences.weightUnit)} / ${UnitFormatter.formatLength(c.lengthCm, preferences.lengthUnit)}"
            lines.add("${dateFmt.format(Date(c.caughtAtEpochMillis))} | ${c.species} | $sizeStr | ${c.locationName}")
        }

        val streamBuilder = StringBuilder("BT\n/F1 11 Tf\n40 790 Td\n15 TL\n")
        lines.forEach { rawLine ->
            val escaped = rawLine
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
            streamBuilder.append("($escaped) Tj T*\n")
        }
        streamBuilder.append("ET\n")
        val contentStream = streamBuilder.toString()

        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n",
            "4 0 obj\n<< /Length ${contentStream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${contentStream}endstream\nendobj\n",
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
        )

        val pdfText = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEach { obj ->
            offsets.add(pdfText.toString().toByteArray(Charsets.US_ASCII).size)
            pdfText.append(obj)
        }
        val xrefStart = pdfText.toString().toByteArray(Charsets.US_ASCII).size
        pdfText.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { off ->
            pdfText.append(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        pdfText.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        return pdfText.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun openExportOutputStream(context: Context, uri: Uri): java.io.OutputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let { java.io.FileOutputStream(java.io.File(it)) }
        } else {
            context.contentResolver.openOutputStream(uri)
        }
    }

    private fun truncateText(input: String, maxChars: Int): String {
        val clean = input.trim()
        return if (clean.length <= maxChars) clean else clean.take(maxChars - 1) + "…"
    }

    private fun escapeCsv(value: String): String {
        val sanitized = value.replace("\"", "\"\"")
        return if (sanitized.any { it == ',' || it == '\n' || it == '\r' || it == '"' }) {
            "\"$sanitized\""
        } else {
            sanitized
        }
    }
}
