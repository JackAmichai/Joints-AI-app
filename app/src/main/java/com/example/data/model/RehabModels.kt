package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class BodyArea(val displayName: String) {
    NECK("Neck"),
    SHOULDER("Shoulder"),
    ELBOW("Elbow"),
    WRIST_HAND("Wrist & Hand"),
    SPINE_BACK("Spine & Back"),
    HIP("Hip"),
    KNEE("Knee"),
    ANKLE_FOOT("Ankle & Foot")
}

enum class TriageStatus(val label: String) {
    PROCEED("Safe to Proceed"),
    PROCEED_WITH_CAUTION("Proceed with Caution"),
    SEEK_PHYSICIAN("Contact Physician"),
    SEEK_EMERGENCY("Go to Emergency Room")
}

data class Exercise(
    val title: String,
    val description: String,
    val targetMuscle: String,
    val sets: Int,
    val reps: Int,
    val durationSeconds: Int = 0,
    val illustrationKey: String = "general_stretch",
    val videoSearchQuery: String = "", // YouTube search query for video demo
)

@Entity(tableName = "user_assessments")
data class UserAssessment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyArea: String, // from BodyArea enum name
    val painIntensity: Int, // 0 to 10
    val freeTextDescription: String,
    val rangeOfMotionQuestionChecked: Boolean,
    val triageStatus: String, // from TriageStatus enum name
    val triageReason: String,
    val scanAngleResult: Double? = null,
    val selectedMovement: String? = null,    // e.g. "Knee Flexion (Heel to glute)"
    val targetAngle: Int? = null,            // e.g. 90
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "exercise_programs")
data class ExerciseProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assessmentId: Long,
    val bodyArea: String,
    val title: String,
    val clinicianStatus: String, // "PENDING", "APPROVED", "ACTIVE", "ARCHIVED"
    val exercises: List<Exercise>,
    val therapistNotes: String? = null,
    val therapistName: String? = null,
    val feedbackRating: Int? = null,
    val feedbackText: String? = null,
    val conditionName: String? = null,       // AI-suggested condition e.g. "FAI / Pincer Impingement"
    val conditionConfidence: Int? = null,     // 0-100 confidence percentage
    val conditionExplanation: String? = null, // brief explanation of the condition
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "exercise_progress")
data class ExerciseProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val exerciseTitle: String,
    val setsCompleted: Int,
    val repsCompleted: Int,
    val painLevelPre: Int,
    val painLevelPost: Int,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class Converters {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val exerciseListType = Types.newParameterizedType(List::class.java, Exercise::class.java)
    private val jsonAdapter = moshi.adapter<List<Exercise>>(exerciseListType)

    @TypeConverter
    fun fromExerciseList(exercises: List<Exercise>?): String? {
        return exercises?.let { jsonAdapter.toJson(it) }
    }

    @TypeConverter
    fun toExerciseList(json: String?): List<Exercise>? {
        return json?.let { jsonAdapter.fromJson(it) }
    }
}
