package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.ChatMessage
import com.example.data.api.GeminiApiClient
import com.example.data.api.JointAnalysisResponse
import com.example.data.api.VideoAnalysisResponse
import com.example.data.db.AppDatabase
import com.example.data.model.BodyArea
import com.example.data.model.Exercise
import com.example.data.model.ExerciseProgram
import com.example.data.model.ExerciseProgress
import com.example.data.model.TriageStatus
import com.example.data.model.UserAssessment
import com.example.data.repository.RehabRepository
import com.example.utils.TriageEvaluation
import com.example.utils.TriageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RehabViewModel(
    application: Application,
    private val repository: RehabRepository
) : AndroidViewModel(application) {

    private val TAG = "RehabViewModel"

    // Database reactive sources
    val assessments: StateFlow<List<UserAssessment>> = repository.allAssessments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val programs: StateFlow<List<ExerciseProgram>> = repository.allPrograms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val progressHistory: StateFlow<List<ExerciseProgress>> = repository.allProgressLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Intake form flows state
    private val _selectedBodyArea = MutableStateFlow<BodyArea?>(null)
    val selectedBodyArea: StateFlow<BodyArea?> = _selectedBodyArea.asStateFlow()

    private val _painIntensity = MutableStateFlow(5)
    val painIntensity: StateFlow<Int> = _painIntensity.asStateFlow()

    private val _painDescription = MutableStateFlow("")
    val painDescription: StateFlow<String> = _painDescription.asStateFlow()

    private val _rangeOfMotionQuestionChecked = MutableStateFlow(false)
    val rangeOfMotionQuestionChecked: StateFlow<Boolean> = _rangeOfMotionQuestionChecked.asStateFlow()

    private val _romImage = MutableStateFlow<Bitmap?>(null)
    val romImage: StateFlow<Bitmap?> = _romImage.asStateFlow()

    private val _romAnalysisResult = MutableStateFlow<JointAnalysisResponse?>(null)
    val romAnalysisResult: StateFlow<JointAnalysisResponse?> = _romAnalysisResult.asStateFlow()

    // --- AI Video Activity assessment flows ---
    private val _selectedTask = MutableStateFlow<String?>(null)
    val selectedTask: StateFlow<String?> = _selectedTask.asStateFlow()

    private val _isAnalyzingVideo = MutableStateFlow(false)
    val isAnalyzingVideo: StateFlow<Boolean> = _isAnalyzingVideo.asStateFlow()

    private val _videoAnalysisResult = MutableStateFlow<VideoAnalysisResponse?>(null)
    val videoAnalysisResult: StateFlow<VideoAnalysisResponse?> = _videoAnalysisResult.asStateFlow()

    private val _isAnalyzingRom = MutableStateFlow(false)
    val isAnalyzingRom: StateFlow<Boolean> = _isAnalyzingRom.asStateFlow()

    private val _triageEvaluation = MutableStateFlow<TriageEvaluation?>(null)
    val triageEvaluation: StateFlow<TriageEvaluation?> = _triageEvaluation.asStateFlow()

    private val _isGeneratingPlan = MutableStateFlow(false)
    val isGeneratingPlan: StateFlow<Boolean> = _isGeneratingPlan.asStateFlow()

    private val _lastGeneratedProgramId = MutableStateFlow<Long?>(null)
    val lastGeneratedProgramId: StateFlow<Long?> = _lastGeneratedProgramId.asStateFlow()

    private val _activeProgram = MutableStateFlow<ExerciseProgram?>(null)
    val activeProgram: StateFlow<ExerciseProgram?> = _activeProgram.asStateFlow()

    // --- AI Chat / QA State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isSendingChat = MutableStateFlow(false)
    val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    init {
        // Automatically check active programs when programs update
        viewModelScope.launch {
            programs.collect { list ->
                // First approved or active program, or first general program
                val act = list.firstOrNull { it.clinicianStatus == "ACTIVE" || it.clinicianStatus == "APPROVED" }
                    ?: list.firstOrNull()
                _activeProgram.value = act
            }
        }
    }

    fun selectBodyArea(area: BodyArea) {
        _selectedBodyArea.value = area
    }

    fun setPainIntensity(intensity: Int) {
        _painIntensity.value = intensity
    }

    fun setPainDescription(desc: String) {
        _painDescription.value = desc
        // Dynamic safety triage assessment as user types
        _triageEvaluation.value = TriageHelper.evaluateTriage(desc)
    }

    fun toggleRangeOfMotionQuestion(checked: Boolean) {
        _rangeOfMotionQuestionChecked.value = checked
    }

    fun setRomImage(bitmap: Bitmap) {
        _romImage.value = bitmap
        analyzeJointRangeOfMotion(bitmap)
    }

    private fun analyzeJointRangeOfMotion(bitmap: Bitmap) {
        _isAnalyzingRom.value = true
        val jointName = _selectedBodyArea.value?.displayName ?: "Joint"
        viewModelScope.launch {
            try {
                val result = GeminiApiClient.analyzeRangeOfMotion(bitmap, jointName)
                _romAnalysisResult.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error matching vision model", e)
            } finally {
                _isAnalyzingRom.value = false
            }
        }
    }

    fun clearIntakeForm() {
        _selectedBodyArea.value = null
        _painIntensity.value = 5
        _painDescription.value = ""
        _rangeOfMotionQuestionChecked.value = false
        _romImage.value = null
        _romAnalysisResult.value = null
        _triageEvaluation.value = null
    }

    fun selectTask(task: String?) {
        _selectedTask.value = task
        _videoAnalysisResult.value = null
    }

    fun analyzeVideoActivity(taskName: String, bitmap: Bitmap?, performanceType: String) {
        _isAnalyzingVideo.value = true
        val bodyAreaName = _activeProgram.value?.bodyArea ?: "KNEE"
        viewModelScope.launch {
            try {
                val result = GeminiApiClient.analyzeActivityVideo(
                    bodyArea = bodyAreaName,
                    activityName = taskName,
                    bitmap = bitmap,
                    performanceType = performanceType
                )
                _videoAnalysisResult.value = result
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing video assessment activity", e)
            } finally {
                _isAnalyzingVideo.value = false
            }
        }
    }

    fun clearVideoAnalysis() {
        _selectedTask.value = null
        _videoAnalysisResult.value = null
        _isAnalyzingVideo.value = false
    }

    // --- AI Chat / QA Methods ---

    /**
     * Send a chat message to the AI assistant.
     * Builds context from the active program for relevant answers.
     */
    fun sendChatMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        _chatError.value = null
        _isSendingChat.value = true

        // Add user message to the list immediately
        val userMsg = ChatMessage(role = "user", text = userMessage)
        _chatMessages.value = _chatMessages.value + userMsg

        // Build program context string
        val programContext = buildProgramContext()

        viewModelScope.launch {
            try {
                val response = GeminiApiClient.sendChatMessage(
                    userMessage = userMessage,
                    conversationHistory = _chatMessages.value.dropLast(1), // Exclude the message we just added
                    programContext = programContext
                )

                val aiMsg = ChatMessage(role = "model", text = response)
                _chatMessages.value = _chatMessages.value + aiMsg
            } catch (e: Exception) {
                Log.e(TAG, "Error sending chat message", e)
                _chatError.value = "Failed to get a response. Please try again."
                val errorMsg = ChatMessage(
                    role = "model",
                    text = "I'm sorry, I encountered an error processing your request. Please try again or contact your clinician directly."
                )
                _chatMessages.value = _chatMessages.value + errorMsg
            } finally {
                _isSendingChat.value = false
            }
        }
    }

    /**
     * Clear the entire chat history.
     */
    fun clearChat() {
        _chatMessages.value = emptyList()
        _chatError.value = null
    }

    /**
     * Build a context string from the active program to give the AI relevant info.
     */
    private fun buildProgramContext(): String {
        val program = _activeProgram.value ?: return "No active program found."
        val exerciseList = program.exercises.joinToString("\n") { ex ->
            "- ${ex.title}: ${ex.sets} sets × ${ex.reps} reps, targeting ${ex.targetMuscle}. ${ex.description}"
        }
        val conditionInfo = if (program.conditionName != null) {
            "\nDiagnosed Condition: ${program.conditionName} (Confidence: ${program.conditionConfidence ?: 0}%)" +
            "\nCondition Details: ${program.conditionExplanation ?: "N/A"}"
        } else ""
        return """
            Program: ${program.title}
            Body Area: ${program.bodyArea}
            Status: ${program.clinicianStatus}
            Therapist: ${program.therapistName ?: "Pending review"}
            Therapist Notes: ${program.therapistNotes ?: "None"}$conditionInfo
            Exercises:
            $exerciseList
        """.trimIndent()
    }

    /**
     * Submits assessment to the local DB and triggers a background generative action.
     * Now also persists the selected movement, target angle, and AI-diagnosed condition.
     */
    fun submitIntakeAndGeneratePlan(
        selectedMovement: String = "",
        targetAngle: Int = 90,
        onComplete: (Long) -> Unit
    ) {
        val area = _selectedBodyArea.value ?: BodyArea.KNEE
        val currentTriage = _triageEvaluation.value ?: TriageHelper.evaluateTriage(_painDescription.value)

        val assessment = UserAssessment(
            bodyArea = area.name,
            painIntensity = _painIntensity.value,
            freeTextDescription = _painDescription.value,
            rangeOfMotionQuestionChecked = _rangeOfMotionQuestionChecked.value,
            triageStatus = currentTriage.status.name,
            triageReason = currentTriage.reason,
            scanAngleResult = _romAnalysisResult.value?.estimatedAngle,
            selectedMovement = selectedMovement.ifBlank { null },
            targetAngle = if (targetAngle > 0) targetAngle else null
        )

        _isGeneratingPlan.value = true

        viewModelScope.launch {
            try {
                // 1. Persist user assessment
                val assessmentId = repository.insertAssessment(assessment)

                // 2. If triage is an emergency halt, don't generate any plans for physical therapy!
                if (currentTriage.status == TriageStatus.SEEK_EMERGENCY || currentTriage.status == TriageStatus.SEEK_PHYSICIAN) {
                    _isGeneratingPlan.value = false
                    onComplete(assessmentId)
                    return@launch
                }

                // 3. Generate structured exercise program via Gemini DPT model
                val response = GeminiApiClient.generateRehabPlan(
                    bodyArea = area.displayName,
                    painIntensity = _painIntensity.value,
                    painDescription = _painDescription.value
                )

                if (response != null) {
                    val hasCautionFlags = currentTriage.status == TriageStatus.PROCEED_WITH_CAUTION
                    val clinicianStatus = "PENDING"

                    val program = ExerciseProgram(
                        assessmentId = assessmentId,
                        bodyArea = area.name,
                        title = response.title,
                        clinicianStatus = clinicianStatus,
                        exercises = response.exercises,
                        conditionName = response.conditionName,
                        conditionConfidence = response.conditionConfidence,
                        conditionExplanation = response.conditionExplanation,
                        therapistNotes = "Pre-loaded guidance: ${response.clinicianGuideline}" + 
                            if (hasCautionFlags) "\n[ALERT-CAUTION]: Checked by safety flag due to symptoms description." else ""
                    )
                    val programId = repository.insertProgram(program)
                    _lastGeneratedProgramId.value = programId
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error submitting intake plan", e)
            } finally {
                _isGeneratingPlan.value = false
                onComplete(0L) // Call onComplete to trigger view navigation
            }
        }
    }

    /**
     * CLINICIAN CAPABILITY: Therapist reviews, edits, and approves the AI-generated schedule.
     */
    fun approveProgram(programId: Long, therapistName: String, therapistNotes: String, reviewedExercises: List<Exercise>) {
        viewModelScope.launch {
            // Find current program
            programs.value.find { it.id == programId }?.let { original ->
                val approvedProgram = original.copy(
                    clinicianStatus = "ACTIVE", // set as ACTIVE to replace previous active plans
                    therapistName = therapistName,
                    therapistNotes = therapistNotes,
                    exercises = reviewedExercises,
                    timestamp = System.currentTimeMillis()
                )
                repository.updateProgram(approvedProgram)
            }
        }
    }

    /**
     * CLINICIAN CAPABILITY: Reject plan.
     */
    fun rejectProgram(programId: Long, notes: String) {
        viewModelScope.launch {
            programs.value.find { it.id == programId }?.let { original ->
                val rejected = original.copy(
                    clinicianStatus = "ARCHIVED",
                    therapistNotes = "Rejected by therapist: $notes"
                )
                repository.updateProgram(rejected)
            }
        }
    }

    /**
     * Log user daily workouts.
     */
    fun logWorkout(programId: Long, exerciseTitle: String, sets: Int, reps: Int, painPre: Int, painPost: Int, note: String) {
        viewModelScope.launch {
            val progress = ExerciseProgress(
                programId = programId,
                exerciseTitle = exerciseTitle,
                setsCompleted = sets,
                repsCompleted = reps,
                painLevelPre = painPre,
                painLevelPost = painPost,
                note = note
            )
            repository.insertProgress(progress)
        }
    }

    fun selectActiveProgram(program: ExerciseProgram) {
        _activeProgram.value = program
    }
}

class RehabViewModelFactory(
    private val application: Application,
    private val repository: RehabRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RehabViewModel::class.java)) {
            return RehabViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
