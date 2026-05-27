package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.ExerciseProgram
import com.example.data.model.ExerciseProgress
import com.example.data.model.UserAssessment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a professional clinical recovery report as a PDF and shares it
 * via Android's share sheet (email, messaging, AirDrop, etc.).
 *
 * Uses Android's built-in PdfDocument API — no third-party libraries needed.
 */
object RecoveryReportGenerator {

    private const val TAG = "RecoveryReportPDF"
    private const val PAGE_WIDTH = 595   // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842  // A4 height in points
    private const val MARGIN = 48f
    private const val LINE_HEIGHT = 16f
    private const val SECTION_GAP = 22f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    // Paint presets
    private fun titlePaint() = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 22f; color = 0xFF0B1120.toInt(); isAntiAlias = true
    }

    private fun headerPaint() = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 14f; color = 0xFF0097A7.toInt(); isAntiAlias = true
    }

    private fun subHeaderPaint() = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 12f; color = 0xFF1C2D42.toInt(); isAntiAlias = true
    }

    private fun bodyPaint() = Paint().apply {
        textSize = 10.5f; color = 0xFF334155.toInt(); isAntiAlias = true
    }

    private fun mutedPaint() = Paint().apply {
        textSize = 9f; color = 0xFF64748B.toInt(); isAntiAlias = true
    }

    private fun accentPaint() = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 11f; color = 0xFF00BCD4.toInt(); isAntiAlias = true
    }

    private fun linePaint() = Paint().apply {
        color = 0xFFCBD5E1.toInt(); strokeWidth = 0.8f; isAntiAlias = true
    }

    private fun warningPaint() = Paint().apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 10f; color = 0xFFD32F2F.toInt(); isAntiAlias = true
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM dd", Locale.US)

    /**
     * Main entry point. Generates the PDF and launches the share sheet.
     */
    fun generateAndShare(
        context: Context,
        program: ExerciseProgram,
        assessment: UserAssessment?,
        progressLogs: List<ExerciseProgress>
    ) {
        try {
            val pdfFile = generatePdf(context, program, assessment, progressLogs)
            sharePdf(context, pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate/share recovery report", e)
        }
    }

    private fun generatePdf(
        context: Context,
        program: ExerciseProgram,
        assessment: UserAssessment?,
        progressLogs: List<ExerciseProgress>
    ): File {
        val document = PdfDocument()
        val pages = mutableListOf<PdfDocument.Page>()
        var currentPage = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        )
        var canvas = currentPage.canvas
        var y = MARGIN
        var pageNumber = 1

        // Helper: start a new page if we're running low on space
        fun ensureSpace(needed: Float): Float {
            if (y + needed > PAGE_HEIGHT - MARGIN - 30f) {
                // Draw page number footer
                canvas.drawText("Page $pageNumber", PAGE_WIDTH / 2f - 20f, PAGE_HEIGHT - 24f, mutedPaint())
                document.finishPage(currentPage)
                pages.add(currentPage)
                pageNumber++
                currentPage = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = currentPage.canvas
                y = MARGIN
            }
            return y
        }

        // Helper: draw wrapped text (returns new y position)
        fun drawWrappedText(text: String, paint: Paint, startY: Float, maxWidth: Float = CONTENT_WIDTH, indent: Float = 0f): Float {
            var currentY = startY
            val words = text.split(" ")
            var line = ""
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) > maxWidth - indent) {
                    currentY = ensureSpace(LINE_HEIGHT)
                    canvas.drawText(line, MARGIN + indent, currentY, paint)
                    currentY += LINE_HEIGHT
                    line = word
                } else {
                    line = test
                }
            }
            if (line.isNotEmpty()) {
                currentY = ensureSpace(LINE_HEIGHT)
                canvas.drawText(line, MARGIN + indent, currentY, paint)
                currentY += LINE_HEIGHT
            }
            return currentY
        }

        // ── HEADER ──────────────────────────────────────

        // Teal accent bar at top
        val barPaint = Paint().apply { color = 0xFF00BCD4.toInt() }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, barPaint)

        y = MARGIN + 14f

        // Title
        canvas.drawText("Recovery Report", MARGIN, y, titlePaint())
        y += 8f

        // App branding
        canvas.drawText("Joints.AI — Clinical Recovery Platform", MARGIN, y + LINE_HEIGHT, mutedPaint())
        y += LINE_HEIGHT + 4f

        // Generation date
        canvas.drawText("Generated: ${dateFormat.format(Date())}", MARGIN, y + LINE_HEIGHT, mutedPaint())
        y += LINE_HEIGHT + SECTION_GAP

        // Divider
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += SECTION_GAP

        // ── PROGRAM OVERVIEW ────────────────────────────

        y = ensureSpace(80f)
        canvas.drawText("PROGRAM OVERVIEW", MARGIN, y, headerPaint())
        y += LINE_HEIGHT + 4f

        // Program title
        canvas.drawText(program.title, MARGIN, y, subHeaderPaint())
        y += LINE_HEIGHT + 2f

        // Body area
        val areaDisplay = program.bodyArea.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " & ")
        canvas.drawText("Body Area: $areaDisplay", MARGIN, y, bodyPaint())
        y += LINE_HEIGHT

        // Status
        val statusText = when (program.clinicianStatus) {
            "ACTIVE" -> "✓ Clinician Approved"
            "PENDING" -> "⏳ Pending Clinical Review"
            "ARCHIVED" -> "Archived"
            else -> program.clinicianStatus
        }
        canvas.drawText("Status: $statusText", MARGIN, y, bodyPaint())
        y += LINE_HEIGHT

        // Therapist
        program.therapistName?.let {
            canvas.drawText("Supervising Clinician: $it", MARGIN, y, bodyPaint())
            y += LINE_HEIGHT
        }

        // Created date
        canvas.drawText("Created: ${dateFormat.format(Date(program.timestamp))}", MARGIN, y, mutedPaint())
        y += LINE_HEIGHT + SECTION_GAP

        // ── AI CONDITION ASSESSMENT ─────────────────────

        if (program.conditionName != null) {
            y = ensureSpace(80f)
            canvas.drawText("AI CONDITION ASSESSMENT", MARGIN, y, headerPaint())
            y += LINE_HEIGHT + 4f

            canvas.drawText("Suspected Condition: ${program.conditionName}", MARGIN, y, subHeaderPaint())
            y += LINE_HEIGHT

            val confLabel = when {
                (program.conditionConfidence ?: 0) >= 70 -> "High"
                (program.conditionConfidence ?: 0) >= 45 -> "Moderate"
                else -> "Low"
            }
            canvas.drawText("Confidence: ${program.conditionConfidence ?: 0}% ($confLabel)", MARGIN, y, accentPaint())
            y += LINE_HEIGHT + 2f

            program.conditionExplanation?.let { explanation ->
                y = drawWrappedText(explanation, bodyPaint(), y)
            }

            y += 4f
            y = drawWrappedText(
                "Note: This is an AI-generated suggestion based on reported symptoms. It is not a clinical diagnosis and should be verified by a licensed physician.",
                warningPaint(), y
            )
            y += SECTION_GAP
        }

        // ── PATIENT ASSESSMENT ──────────────────────────

        if (assessment != null) {
            y = ensureSpace(100f)
            canvas.drawLine(MARGIN, y - 6f, PAGE_WIDTH - MARGIN, y - 6f, linePaint())
            y += 8f
            canvas.drawText("PATIENT ASSESSMENT", MARGIN, y, headerPaint())
            y += LINE_HEIGHT + 4f

            // Data rows
            fun dataRow(label: String, value: String) {
                y = ensureSpace(LINE_HEIGHT)
                canvas.drawText(label, MARGIN, y, bodyPaint())
                canvas.drawText(value, MARGIN + 180f, y, subHeaderPaint())
                y += LINE_HEIGHT
            }

            dataRow("Pain Intensity:", "${assessment.painIntensity} / 10")
            dataRow("Triage Status:", assessment.triageStatus.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })

            assessment.selectedMovement?.let { dataRow("Movement Tested:", it) }
            assessment.targetAngle?.let { dataRow("Target Angle:", "${it}°") }
            assessment.scanAngleResult?.let { dataRow("Measured ROM:", "${it}°") }

            // Symptoms
            val symptoms = assessment.freeTextDescription.substringBefore("\n\n[Clinical AI")
            if (symptoms.isNotBlank()) {
                y += 4f
                canvas.drawText("Patient-Reported Symptoms:", MARGIN, y, bodyPaint().apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                })
                y += LINE_HEIGHT
                y = drawWrappedText("\"$symptoms\"", bodyPaint().apply {
                    textSize = 10f; color = 0xFF475569.toInt()
                }, y, indent = 8f)
            }

            y += SECTION_GAP
        }

        // ── EXERCISE PROGRAM ────────────────────────────

        y = ensureSpace(40f)
        canvas.drawLine(MARGIN, y - 6f, PAGE_WIDTH - MARGIN, y - 6f, linePaint())
        y += 8f
        canvas.drawText("PRESCRIBED EXERCISES (${program.exercises.size})", MARGIN, y, headerPaint())
        y += LINE_HEIGHT + 6f

        program.exercises.forEachIndexed { index, exercise ->
            y = ensureSpace(90f)

            // Exercise number + title
            canvas.drawText("${index + 1}. ${exercise.title}", MARGIN, y, subHeaderPaint())
            y += LINE_HEIGHT

            // Sets / reps / muscle
            val prescription = buildString {
                append("${exercise.sets} sets × ${exercise.reps} reps")
                if (exercise.durationSeconds > 0) append("  •  Hold: ${exercise.durationSeconds}s")
                append("  •  Target: ${exercise.targetMuscle}")
            }
            canvas.drawText(prescription, MARGIN + 12f, y, accentPaint().apply { textSize = 10f })
            y += LINE_HEIGHT + 2f

            // Description
            y = drawWrappedText(exercise.description, bodyPaint(), y, indent = 12f)
            y += 10f
        }

        // ── THERAPIST NOTES ─────────────────────────────

        program.therapistNotes?.let { notes ->
            val cleanNotes = notes.substringBefore("\n[ALERT-CAUTION]")
                .removePrefix("Pre-loaded guidance: ")
            if (cleanNotes.isNotBlank()) {
                y = ensureSpace(60f)
                canvas.drawLine(MARGIN, y - 6f, PAGE_WIDTH - MARGIN, y - 6f, linePaint())
                y += 8f
                canvas.drawText("CLINICAL NOTES", MARGIN, y, headerPaint())
                y += LINE_HEIGHT + 4f
                y = drawWrappedText(cleanNotes, bodyPaint(), y)
                y += SECTION_GAP
            }
        }

        // ── PROGRESS HISTORY ────────────────────────────

        val programLogs = progressLogs.filter { it.programId == program.id }
        if (programLogs.isNotEmpty()) {
            y = ensureSpace(60f)
            canvas.drawLine(MARGIN, y - 6f, PAGE_WIDTH - MARGIN, y - 6f, linePaint())
            y += 8f
            canvas.drawText("WORKOUT HISTORY (${programLogs.size} sessions)", MARGIN, y, headerPaint())
            y += LINE_HEIGHT + 6f

            // Table header
            y = ensureSpace(LINE_HEIGHT)
            val tableHeaderPaint = mutedPaint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textSize = 9f
            }
            canvas.drawText("DATE", MARGIN, y, tableHeaderPaint)
            canvas.drawText("EXERCISE", MARGIN + 72f, y, tableHeaderPaint)
            canvas.drawText("SETS", MARGIN + 280f, y, tableHeaderPaint)
            canvas.drawText("REPS", MARGIN + 320f, y, tableHeaderPaint)
            canvas.drawText("PRE", MARGIN + 365f, y, tableHeaderPaint)
            canvas.drawText("POST", MARGIN + 405f, y, tableHeaderPaint)
            y += 2f
            canvas.drawLine(MARGIN, y + 4f, PAGE_WIDTH - MARGIN, y + 4f, linePaint())
            y += LINE_HEIGHT

            val tableBodyPaint = bodyPaint().apply { textSize = 9f }

            programLogs.sortedByDescending { it.timestamp }.take(30).forEach { log ->
                y = ensureSpace(LINE_HEIGHT)
                canvas.drawText(shortDateFormat.format(Date(log.timestamp)), MARGIN, y, tableBodyPaint)

                // Truncate exercise title to fit
                val truncTitle = if (log.exerciseTitle.length > 32) log.exerciseTitle.take(30) + "…" else log.exerciseTitle
                canvas.drawText(truncTitle, MARGIN + 72f, y, tableBodyPaint)
                canvas.drawText("${log.setsCompleted}", MARGIN + 280f, y, tableBodyPaint)
                canvas.drawText("${log.repsCompleted}", MARGIN + 320f, y, tableBodyPaint)
                canvas.drawText("${log.painLevelPre}", MARGIN + 365f, y, tableBodyPaint)
                canvas.drawText("${log.painLevelPost}", MARGIN + 405f, y, tableBodyPaint)
                y += LINE_HEIGHT
            }

            if (programLogs.size > 30) {
                y += 4f
                canvas.drawText("(Showing most recent 30 of ${programLogs.size} sessions)", MARGIN, y, mutedPaint())
                y += LINE_HEIGHT
            }

            y += SECTION_GAP
        }

        // ── FOOTER ──────────────────────────────────────

        y = ensureSpace(60f)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint())
        y += LINE_HEIGHT + 4f
        canvas.drawText("This report was auto-generated by Joints.AI.", MARGIN, y, mutedPaint())
        y += LINE_HEIGHT
        canvas.drawText("For clinical decisions, consult the supervising physiotherapist.", MARGIN, y, mutedPaint())
        y += LINE_HEIGHT
        canvas.drawText("© ${SimpleDateFormat("yyyy", Locale.US).format(Date())} Joints.AI — Confidential Patient Record", MARGIN, y, mutedPaint())

        // Final page number
        canvas.drawText("Page $pageNumber", PAGE_WIDTH / 2f - 20f, PAGE_HEIGHT - 24f, mutedPaint())
        document.finishPage(currentPage)

        // Write PDF to cache
        val reportsDir = File(context.cacheDir, "reports")
        reportsDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "JointsAI_Recovery_Report_${areaDisplay.replace(" ", "")}__${timestamp}.pdf"
        val pdfFile = File(reportsDir, fileName)

        FileOutputStream(pdfFile).use { outputStream ->
            document.writeTo(outputStream)
        }
        document.close()

        Log.d(TAG, "PDF generated: ${pdfFile.absolutePath} (${pdfFile.length() / 1024} KB)")
        return pdfFile
    }

    private fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Joints.AI Recovery Report")
            putExtra(Intent.EXTRA_TEXT, "Please find the patient's recovery report from Joints.AI attached.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share Recovery Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
