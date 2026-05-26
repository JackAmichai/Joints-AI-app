package com.example.utils

import com.example.data.model.TriageStatus

data class RedFlagHit(
    val category: String,
    val matchedPhrase: String,
    val userMessage: String,
    val severity: String // "critical_er", "critical_physician", "caution"
)

object TriageHelper {

    private val ER_RED_FLAGS = listOf(
        "bladder", "bowel", "groin", "saddle anesthesia", "groin numbness", 
        "cannot control", "paralysis", "sudden weakness", "chest pain", "shortness of breath"
    )

    private val PHYSICIAN_RED_FLAGS = listOf(
        "weight loss", "night sweats", "history of cancer", "unexplained", 
        "fever and chills", "recent fracture", "severe trauma", "car crash", "major fall"
    )

    private val CAUTION_FLAGS = listOf(
        "numbness", "tingling", "pins and needles", "radiating", "shooting pain", 
        "progressive weakness", "dizziness", "clicks"
    )

    /**
     * Scan the text description of the pain for physical therapy red flags.
     */
    fun evaluateTriage(text: String): TriageEvaluation {
        val cleanText = text.lowercase()
        val erHits = mutableListOf<String>()
        val mdHits = mutableListOf<String>()
        val cautionHits = mutableListOf<String>()

        for (flag in ER_RED_FLAGS) {
            if (cleanText.contains(flag)) erHits.add(flag)
        }
        for (flag in PHYSICIAN_RED_FLAGS) {
            if (cleanText.contains(flag)) mdHits.add(flag)
        }
        for (flag in CAUTION_FLAGS) {
            if (cleanText.contains(flag)) cautionHits.add(flag)
        }

        return when {
            erHits.isNotEmpty() -> TriageEvaluation(
                status = TriageStatus.SEEK_EMERGENCY,
                reason = "Matched safety critical red flag: '${erHits.first()}'. This requires immediate emergency clinical evaluation.",
                matchedCautionFlags = erHits
            )
            mdHits.isNotEmpty() -> TriageEvaluation(
                status = TriageStatus.SEEK_PHYSICIAN,
                reason = "Matched high severity flag: '${mdHits.first()}'. We recommend urgent consultation with an orthopedic physician before commencing exercises.",
                matchedCautionFlags = mdHits
            )
            cautionHits.isNotEmpty() -> TriageEvaluation(
                status = TriageStatus.PROCEED_WITH_CAUTION,
                reason = "Caution flags identified: '${cautionHits.first()}'. AI plan will be flagged for stricter, immediate, clinical human reviewer assessment.",
                matchedCautionFlags = cautionHits
            )
            else -> TriageEvaluation(
                status = TriageStatus.PROCEED,
                reason = "No emergency red flags matched. Generating physical therapy recovery exercises...",
                matchedCautionFlags = emptyList()
            )
        }
    }
}

data class TriageEvaluation(
    val status: TriageStatus,
    val reason: String,
    val matchedCautionFlags: List<String>
)
