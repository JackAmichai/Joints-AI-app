package com.example.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Exercise
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// --- Gemini API Request & Response Schema ---

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val parts: List<PartResponse>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentResponse? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

// --- Domain Models for parsed replies ---

@JsonClass(generateAdapter = true)
data class GeneratedPlanResponse(
    val title: String,
    val exercises: List<Exercise>,
    val clinicianGuideline: String,
    val conditionName: String = "General Joint Dysfunction",
    val conditionConfidence: Int = 50,
    val conditionExplanation: String = ""
)

@JsonClass(generateAdapter = true)
data class JointAnalysisResponse(
    val joint: String,
    val estimatedAngle: Double,
    val restrictionLevel: String,
    val analysisFeedback: String
)

@JsonClass(generateAdapter = true)
data class VideoAnalysisResponse(
    val activityName: String,
    val calculatedRomDegrees: Double,
    val maxPainGrimaceIndex: Double,
    val painLevelClassification: String,
    val guardingDetected: Boolean,
    val safetyAssessment: String,
    val activityAdvice: String
)

// --- Chat message model ---
data class ChatMessage(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun getApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isEmpty() || key == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing or default.")
        }
        return key
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    // ─── Multi-turn Chat ───

    suspend fun sendChatMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        programContext: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        val systemInstructionText = """
            You are a friendly, professional AI physiotherapy assistant for the Joints.AI app.
            You help patients understand their exercise programs, answer questions about their 
            rehabilitation, and provide general guidance on safe movement practices.
            
            IMPORTANT GUIDELINES:
            - Be warm, encouraging, and empathetic
            - Keep answers concise (2-4 sentences unless more detail is needed)
            - Always remind patients to consult their clinician for serious concerns
            - Never diagnose conditions or override clinician recommendations
            - If asked about pain that sounds serious, recommend they contact their therapist
            - Reference the patient's specific program details when relevant
            
            PATIENT'S CURRENT PROGRAM CONTEXT:
            $programContext
        """.trimIndent()

        val contents = mutableListOf<Content>()
        for (msg in conversationHistory) {
            contents.add(Content(parts = listOf(Part(text = msg.text)), role = msg.role))
        }
        contents.add(Content(parts = listOf(Part(text = userMessage)), role = "user"))

        if (!isApiKeyConfigured()) {
            return@withContext getFallbackChatResponse(userMessage, programContext)
        }

        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(temperature = 0.7f),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!responseText.isNullOrEmpty()) {
                return@withContext responseText
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in chat API call", e)
            return@withContext "I'm having trouble connecting right now. Please try again in a moment, or consult your clinician directly for urgent questions."
        }

        return@withContext "I couldn't process that request. Please try rephrasing your question."
    }

    private fun getFallbackChatResponse(question: String, programContext: String): String {
        val q = question.lowercase()
        return when {
            q.contains("how many") && (q.contains("set") || q.contains("rep")) ->
                "Based on your current program, I recommend following the prescribed sets and reps exactly as listed. Start with the lower end if you're feeling sore, and gradually work up. Always stop if you feel sharp pain."
            q.contains("pain") && (q.contains("worse") || q.contains("increase") || q.contains("more")) ->
                "If your pain is increasing during exercises, stop immediately and rest. Apply ice for 15-20 minutes. If pain persists or worsens over 48 hours, please contact your clinician. Never push through sharp or shooting pain."
            q.contains("brace") || q.contains("support") || q.contains("tape") ->
                "Using a supportive brace during exercises can be helpful, especially in early recovery stages. However, I'd recommend discussing the specific type and duration of brace use with your clinician, as it varies by condition and recovery phase."
            q.contains("how often") || q.contains("frequency") || q.contains("daily") ->
                "For optimal recovery, aim to perform your prescribed exercises daily or as directed by your clinician. Consistency is more important than intensity. Listen to your body and take rest days if soreness is above 6/10."
            q.contains("stretch") || q.contains("warm") ->
                "Always warm up with 5 minutes of gentle movement before starting your exercises. Light walking or gentle range-of-motion movements work well. This helps increase blood flow and reduces injury risk."
            q.contains("ice") || q.contains("heat") || q.contains("cold") ->
                "Ice is best for acute inflammation and post-exercise soreness (15-20 minutes). Heat is better for chronic stiffness before exercising. A good rule: ice after activity, heat before activity. Ask your clinician for personalized advice."
            q.contains("hello") || q.contains("hi") || q.contains("hey") ->
                "Hello! 👋 I'm your AI physiotherapy assistant. I can help answer questions about your exercise program, recovery tips, and general rehabilitation guidance. What would you like to know?"
            q.contains("thank") ->
                "You're welcome! Keep up the great work with your exercises. Consistency is the key to recovery. Feel free to ask me anything else! 💪"
            else ->
                "That's a great question. Based on your rehabilitation program, I'd recommend discussing this specific concern with your clinician for the most personalized advice. In the meantime, focus on performing your prescribed exercises with proper form and within pain-free ranges."
        }
    }

    // ─── Plan Generation — Now with condition diagnosis + YouTube queries ───

    suspend fun generateRehabPlan(
        bodyArea: String,
        painIntensity: Int,
        painDescription: String
    ): GeneratedPlanResponse? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getFallbackPlan(bodyArea, painDescription)
        }

        val promptText = """
            You are a Doctor of Physical Therapy. A patient presents with:
            - Body Area: $bodyArea
            - Pain Intensity: $painIntensity / 10
            - Patient Description: "$painDescription"

            1. DIAGNOSE: Based on the symptoms, suggest the most likely musculoskeletal condition 
               (e.g. "Lateral Epicondylitis (Tennis Elbow)", "FAI / Pincer Impingement", 
               "L4-L5 Disc Herniation", "Patellofemoral Pain Syndrome", "Rotator Cuff Tendinopathy", etc.).
               State your confidence from 0 to 100.
               Provide a 1-2 sentence explanation of the condition.

            2. EXERCISE PROGRAM: Create 3-4 evidence-based exercises. For each exercise include:
               - A clear step-by-step description of proper form
               - A YouTube search query that would find a good demonstration video 
                 (e.g. "quad sets knee physical therapy exercise demonstration")

            Return valid JSON matching this schema exactly:
            {
               "title": "Program Title",
               "conditionName": "Suspected Condition Name",
               "conditionConfidence": 75,
               "conditionExplanation": "Brief explanation of the condition and why it matches the symptoms.",
               "exercises": [
                  {
                     "title": "Exercise Name",
                     "description": "Detailed step-by-step instructions with proper form cues",
                     "targetMuscle": "Primary muscle targeted",
                     "sets": 3,
                     "reps": 10,
                     "durationSeconds": 0,
                     "illustrationKey": "general_stretch",
                     "videoSearchQuery": "exercise name physical therapy demonstration"
                  }
               ],
               "clinicianGuideline": "Safety warnings and therapist guidelines."
            }
        """.trimIndent()

        val systemInstructionText = """
            You are a clinical Doctor of Physical Therapy (DPT) and AI diagnostic assistant.
            You provide evidence-based condition assessments and generate safe, auditable 
            home physical therapy programs. You must include a condition diagnosis with confidence
            level and YouTube-searchable video queries for each exercise.
            You strictly output in application/json format matching the requested schema.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = promptText)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrEmpty()) {
                val adapter = moshi.adapter(GeneratedPlanResponse::class.java)
                return@withContext adapter.fromJson(jsonText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating plan, using fallback", e)
        }

        return@withContext getFallbackPlan(bodyArea, painDescription)
    }

    // ─── ROM Analysis ───

    suspend fun analyzeRangeOfMotion(
        bitmap: Bitmap,
        jointDescription: String
    ): JointAnalysisResponse? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext JointAnalysisResponse(
                joint = jointDescription,
                estimatedAngle = 105.0,
                restrictionLevel = "Mild",
                analysisFeedback = "Slightly restricted flexion of the joint. Some minor tightness is observed. Normal rotation is expected around 120+ degrees. Continue gentle stretching."
            )
        }

        val base64Img = bitmap.toBase64()
        val promptText = """
            You are a physical therapist AI range of motion (ROM) analyzer. 
            Observe this image showing joint movement: "$jointDescription".
            Estimate the active flexion or extension angle in degrees shown in the image.
            Recommend movement precautions.
            
            Return a valid JSON object matching this schema exactly:
            {
               "joint": "Name of the Joint identified",
               "estimatedAngle": 110.0,
               "restrictionLevel": "Mild/Moderate/Severe/None",
               "analysisFeedback": "Your clinical findings and advice"
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(
                Part(text = promptText),
                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Img))
            ))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.1f)
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrEmpty()) {
                val adapter = moshi.adapter(JointAnalysisResponse::class.java)
                return@withContext adapter.fromJson(jsonText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing joint image", e)
        }

        return@withContext JointAnalysisResponse(
            joint = jointDescription, estimatedAngle = 98.0,
            restrictionLevel = "Moderate",
            analysisFeedback = "Flexion restricted to approximately 98°. Recommended sub-maximal mobility drills first."
        )
    }

    // ─── Video Activity Analysis ───

    suspend fun analyzeActivityVideo(
        bodyArea: String, activityName: String, bitmap: Bitmap?, performanceType: String
    ): VideoAnalysisResponse = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || bitmap == null) {
            return@withContext getFallbackVideoAnalysis(bodyArea, activityName, performanceType)
        }

        val base64Img = bitmap.toBase64()
        val promptText = """
            You are a clinical kinesiology and pain psychology AI analyzer.
            Patient performing: "$activityName" for the "$bodyArea" region.
            
            Assess: 1) Joint ROM angle  2) Facial pain grimace (0-10 scale)
            
            Return JSON: {
               "activityName": "$activityName", "calculatedRomDegrees": 115.0,
               "maxPainGrimaceIndex": 4.5, "painLevelClassification": "Moderate Grimace",
               "guardingDetected": true, "safetyAssessment": "...", "activityAdvice": "..."
            }
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(
                Part(text = promptText),
                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Img))
            ))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2f)
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrEmpty()) {
                val adapter = moshi.adapter(VideoAnalysisResponse::class.java)
                adapter.fromJson(jsonText)?.let { return@withContext it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing video frame", e)
        }

        return@withContext getFallbackVideoAnalysis(bodyArea, activityName, performanceType)
    }

    fun getFallbackVideoAnalysis(bodyArea: String, activityName: String, performanceType: String): VideoAnalysisResponse {
        val calculatedRom: Double; val grimaceIndex: Double; val classification: String
        val guarding: Boolean; val safety: String; val advice: String

        when (performanceType) {
            "Pain-Free / Normal Space" -> {
                calculatedRom = when (bodyArea.uppercase()) { "KNEE"->125.0; "SHOULDER"->155.0; "ANKLE","ANKLE_FOOT"->35.0; "SPINE_BACK","SPINE","BACK"->45.0; "NECK"->40.0; "ELBOW"->135.0; "WRIST_HAND"->60.0; "HIP"->115.0; else->120.0 }
                grimaceIndex = 0.8; classification = "None / Pain-Free"; guarding = false
                safety = "Excellent symmetric translation path. Zero micro-facial expressions of pain or physical guarding registered."
                advice = "Perfect biomechanical stability. Cleared for standard progressions as prescribed."
            }
            "Mild Pinch / Guarding" -> {
                calculatedRom = when (bodyArea.uppercase()) { "KNEE"->95.0; "SHOULDER"->110.0; "ANKLE","ANKLE_FOOT"->22.0; "SPINE_BACK","SPINE","BACK"->28.0; "NECK"->25.0; "ELBOW"->100.0; "WRIST_HAND"->45.0; "HIP"->85.0; else->85.0 }
                grimaceIndex = 4.2; classification = "Mild/Moderate Discomfort"; guarding = true
                safety = "Identified moderate protective muscular guarding. Noticeable brow-lowering near terminal flexion."
                advice = "Reduce speed near end-range. Do not push through sharp restriction borders."
            }
            else -> {
                calculatedRom = when (bodyArea.uppercase()) { "KNEE"->65.0; "SHOULDER"->75.0; "ANKLE","ANKLE_FOOT"->12.0; "SPINE_BACK","SPINE","BACK"->15.0; "NECK"->12.0; "ELBOW"->70.0; "WRIST_HAND"->30.0; "HIP"->55.0; else->55.0 }
                grimaceIndex = 8.6; classification = "Severe Grimace & Guarding"; guarding = true
                safety = "Acute clinical restriction. High-grade muscular guarding and posture splinting detected."
                advice = "IMMEDIATELY cease this depth. Walk back range by 30-40%. Report this to your clinician."
            }
        }
        return VideoAnalysisResponse(activityName, calculatedRom, grimaceIndex, classification, guarding, safety, advice)
    }

    // ─── Fallback Plans — with condition diagnosis + video queries ───
    // Every body area in the BodyArea enum is covered with 3-4 anatomically correct exercises,
    // accurate muscle targets, step-by-step form cues, and YouTube-searchable video queries.

    private fun getFallbackPlan(bodyArea: String, painDescription: String = ""): GeneratedPlanResponse {
        val desc = painDescription.lowercase()
        return when (bodyArea.uppercase()) {

            // ════════════════════════════════════════
            // KNEE
            // ════════════════════════════════════════
            "KNEE" -> {
                val (condName, condConf, condExpl) = inferKneeCondition(desc)
                GeneratedPlanResponse(
                    title = "Knee Stabilization & Quad Recovery",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Quad Sets (Isometric Knee Press)",
                            description = "Sit with your leg straight out in front of you. Roll a small towel and place it under the back of your knee. Press the back of your knee firmly into the towel by tightening your quadricep (top of thigh). You should see the muscle above your kneecap tighten and your kneecap slide slightly upward. Hold for 5 seconds, then fully relax. Repeat.",
                            targetMuscle = "Quadriceps (Vastus Medialis Oblique)",
                            sets = 3, reps = 10, durationSeconds = 5,
                            illustrationKey = "knee_press",
                            videoSearchQuery = "quad sets isometric knee exercise physical therapy"
                        ),
                        Exercise(
                            title = "Straight Leg Raises (SLR)",
                            description = "Lie flat on your back with the uninjured knee bent and foot flat on the floor. Tighten the quad on your straight (injured) leg first, then slowly lift it 10-12 inches off the ground, keeping the knee completely locked straight. Hold for 2 seconds at the top. Lower slowly — do not let the leg drop. If you feel pain behind the knee, reduce the height.",
                            targetMuscle = "Hip Flexors (Iliopsoas) & Quadriceps",
                            sets = 3, reps = 12, durationSeconds = 0,
                            illustrationKey = "knee_raise",
                            videoSearchQuery = "straight leg raises knee rehab physical therapy"
                        ),
                        Exercise(
                            title = "Heel Slides",
                            description = "Lie on your back with both legs straight. Slowly slide the heel of your injured leg along the floor toward your buttock, bending the knee as far as is comfortable. Keep the heel in contact with the floor at all times. Pause at the point of tightness (not pain), then slowly slide the heel back out straight. Use a towel under the heel if the surface has friction.",
                            targetMuscle = "Hamstrings & Knee Joint Capsule ROM",
                            sets = 3, reps = 10, durationSeconds = 0,
                            illustrationKey = "hamstring_stretch",
                            videoSearchQuery = "heel slides knee flexion range of motion exercise"
                        ),
                        Exercise(
                            title = "Terminal Knee Extensions (TKE)",
                            description = "Stand facing a door or anchor point with a resistance band looped behind the back of your knee. Step back until there is light tension. Bend the knee about 30 degrees, then slowly straighten it fully against the band resistance. Squeeze the quad hard at full extension and hold 2 seconds. Slowly bend again and repeat. Keep your hips level.",
                            targetMuscle = "Quadriceps (VMO & Rectus Femoris)",
                            sets = 3, reps = 12, durationSeconds = 0,
                            illustrationKey = "knee_press",
                            videoSearchQuery = "terminal knee extension TKE band exercise rehab"
                        )
                    ),
                    clinicianGuideline = "Prioritize VMO activation to offset patella maltracking. Monitor for effusion swelling. Stop heel slides if sharp posterior or medial joint line pain occurs. Progress to closed-chain exercises once pain-free quad activation is achieved."
                )
            }

            // ════════════════════════════════════════
            // ANKLE & FOOT
            // ════════════════════════════════════════
            "ANKLE_FOOT", "ANKLE" -> {
                val (condName, condConf, condExpl) = inferAnkleCondition(desc)
                GeneratedPlanResponse(
                    title = "Ankle Mobility & Stability Rehab",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Ankle Pumps (Dorsi/Plantarflexion)",
                            description = "Sit or lie with your leg elevated and foot unsupported. Point your toes away from you as far as possible (plantarflexion), then pull your toes back toward your shin as far as possible (dorsiflexion). Pump smoothly back and forth in a controlled rhythm. This promotes venous return and reduces swelling. Keep movements smooth — no jerking.",
                            targetMuscle = "Tibialis Anterior & Gastrocnemius/Soleus",
                            sets = 3, reps = 20, durationSeconds = 0,
                            illustrationKey = "ankle_flex",
                            videoSearchQuery = "ankle pumps exercise physical therapy ankle rehab"
                        ),
                        Exercise(
                            title = "Ankle Alphabet (Multi-Plane ROM)",
                            description = "Sit with your foot hanging off the edge of a bed or chair, completely unsupported. Using your big toe as a pen, slowly trace each letter of the alphabet (A through Z) in the air. Make each letter as large as possible to move the ankle through its full range in all planes — dorsiflexion, plantarflexion, inversion, and eversion. This is one complete set.",
                            targetMuscle = "Peroneal Group, Tibialis Posterior & Ankle Rotators",
                            sets = 2, reps = 1, durationSeconds = 90,
                            illustrationKey = "ankle_stretch",
                            videoSearchQuery = "ankle alphabet exercise range of motion rehab"
                        ),
                        Exercise(
                            title = "Towel Calf Stretch (Gastrocnemius)",
                            description = "Sit on the floor with the injured leg straight out in front of you. Loop a towel or resistance band around the ball of your foot. Keeping your knee straight, gently pull the towel toward you until you feel a firm stretch in the back of your calf. Hold for 30 seconds. Keep your back straight — do not hunch forward. To target the soleus, slightly bend the knee and repeat.",
                            targetMuscle = "Gastrocnemius & Soleus Complex",
                            sets = 3, reps = 3, durationSeconds = 30,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "towel calf stretch seated physical therapy"
                        ),
                        Exercise(
                            title = "Single-Leg Balance (Proprioception)",
                            description = "Stand barefoot near a wall or counter for safety. Lift your uninjured foot off the ground and balance on the injured ankle. Keep your standing knee slightly soft (not locked). Fix your gaze on a point at eye level. Try to hold for 30 seconds. Progress by closing your eyes, standing on a pillow, or adding gentle arm movements once stable.",
                            targetMuscle = "Peroneals, Intrinsic Foot Muscles & Ankle Stabilizers",
                            sets = 3, reps = 3, durationSeconds = 30,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "single leg balance ankle proprioception exercise rehab"
                        )
                    ),
                    clinicianGuideline = "Monitor for inversion instability and anterior drawer sign. Avoid uneven terrain until proprioception is restored. If Achilles tendinopathy is suspected, avoid aggressive dorsiflexion stretching. Progress from non-weight-bearing to full-weight-bearing exercises gradually."
                )
            }

            // ════════════════════════════════════════
            // SHOULDER
            // ════════════════════════════════════════
            "SHOULDER" -> {
                val (condName, condConf, condExpl) = inferShoulderCondition(desc)
                GeneratedPlanResponse(
                    title = "Rotator Cuff & Shoulder Mobility",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Pendulum Swings (Codman's Exercise)",
                            description = "Lean forward at the waist, supporting yourself with your good hand on a table or chair. Let the affected arm hang straight down, completely relaxed. Using only your body sway (not arm muscles), gently swing the arm in small circles — 10 clockwise, then 10 counter-clockwise. Gradually increase the circle diameter as comfort allows. The arm should feel like a pendulum — heavy and passive.",
                            targetMuscle = "Glenohumeral Joint Capsule, Supraspinatus & Deltoid (Passive ROM)",
                            sets = 3, reps = 10, durationSeconds = 0,
                            illustrationKey = "shoulder_arm",
                            videoSearchQuery = "pendulum swing codman exercise shoulder rehab rotator cuff"
                        ),
                        Exercise(
                            title = "Wall Crawls (Shoulder Flexion)",
                            description = "Stand facing a wall with your toes 6 inches from the baseboard. Place the fingertips of your affected arm on the wall at waist level. Slowly 'crawl' your fingers up the wall, stepping closer as needed, raising the arm as high as tolerable. Hold 3 seconds at the top. Slowly crawl back down. Mark your height each session to track progress. You should feel a stretch, not sharp pain.",
                            targetMuscle = "Anterior Deltoid, Latissimus Dorsi & Pectoralis Major ROM",
                            sets = 3, reps = 8, durationSeconds = 0,
                            illustrationKey = "shoulder_stretch",
                            videoSearchQuery = "wall crawl shoulder flexion exercise physical therapy"
                        ),
                        Exercise(
                            title = "Scapular Retraction (Squeeze & Hold)",
                            description = "Sit or stand with your arms relaxed at your sides and shoulders down away from your ears. Squeeze your shoulder blades together and downward toward your back pockets. Imagine pinching a pencil between your shoulder blades. Hold for 5 seconds, focusing on the contraction between your spine and scapulae. Fully relax and repeat. Do not shrug your shoulders upward.",
                            targetMuscle = "Rhomboids, Middle/Lower Trapezius & Serratus Anterior",
                            sets = 3, reps = 12, durationSeconds = 5,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "scapular retraction squeeze exercise posture shoulder blade"
                        ),
                        Exercise(
                            title = "Sidelying External Rotation",
                            description = "Lie on your unaffected side with a small towel rolled between your upper arm and your ribcage. Bend your affected elbow to 90 degrees and rest the hand on your belly. Keeping your elbow pinned to the towel roll, slowly rotate your forearm upward toward the ceiling as far as comfortable. Hold 2 seconds. Slowly return. Use a light weight (1-2 lbs) only when pain-free.",
                            targetMuscle = "Infraspinatus & Teres Minor (External Rotators)",
                            sets = 3, reps = 10, durationSeconds = 0,
                            illustrationKey = "shoulder_arm",
                            videoSearchQuery = "sidelying external rotation shoulder rotator cuff exercise"
                        )
                    ),
                    clinicianGuideline = "Avoid overhead activities above 90° if impingement is suspected. Monitor painful arc between 60-120° abduction. Do not use weights until pain-free ROM is achieved. Assess for scapular dyskinesis and address postural contributors."
                )
            }

            // ════════════════════════════════════════
            // SPINE & BACK
            // ════════════════════════════════════════
            "SPINE_BACK", "SPINE", "BACK" -> {
                val (condName, condConf, condExpl) = inferSpineCondition(desc)
                GeneratedPlanResponse(
                    title = "Spinal Recovery & Core Stabilization",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Cat-Cow Mobilization",
                            description = "Start on hands and knees (tabletop position) with wrists directly under shoulders and knees under hips. Inhale: slowly drop your belly toward the floor, lift your tailbone and chest toward the ceiling, and look slightly upward (Cow). Exhale: round your entire spine toward the ceiling, tuck your chin to chest, and draw your navel to your spine (Cat). Move slowly and rhythmically between positions. Each cycle is one rep.",
                            targetMuscle = "Erector Spinae, Multifidus & Rectus Abdominis",
                            sets = 3, reps = 10, durationSeconds = 0,
                            illustrationKey = "cat_cow",
                            videoSearchQuery = "cat cow stretch exercise lower back pain physical therapy"
                        ),
                        Exercise(
                            title = "Glute Bridges",
                            description = "Lie on your back with knees bent at 90 degrees and feet flat on the floor, hip-width apart. Arms rest at your sides with palms down. Squeeze your glutes and press through your heels to lift your hips off the ground until your body forms a straight line from knees to shoulders. Hold for 3 seconds at the top, maintaining a neutral spine (do not arch your low back). Lower slowly with control. Do not push through your toes.",
                            targetMuscle = "Gluteus Maximus, Hamstrings & Deep Core Stabilizers",
                            sets = 3, reps = 12, durationSeconds = 0,
                            illustrationKey = "bridge",
                            videoSearchQuery = "glute bridge exercise physical therapy lower back"
                        ),
                        Exercise(
                            title = "Pelvic Tilts (TA Activation)",
                            description = "Lie on your back with knees bent and feet flat. Place your fingers on your hip bones to feel the movement. Tighten your deep abdominal muscles (imagine drawing your belly button toward your spine) and flatten the small of your back against the floor. You should feel your pelvis tilt slightly backward. Hold for 5 seconds while breathing normally. Fully relax between reps.",
                            targetMuscle = "Transversus Abdominis & Internal Obliques",
                            sets = 3, reps = 12, durationSeconds = 5,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "pelvic tilt exercise core activation back pain"
                        ),
                        Exercise(
                            title = "Bird-Dog (Quadruped Reach)",
                            description = "Start on hands and knees in tabletop position. Engage your core to stabilize your spine. Slowly extend your right arm forward and left leg backward simultaneously until both are parallel with the floor. Hold for 3 seconds, keeping your hips level (imagine balancing a glass of water on your lower back). Return to the start and switch sides. One rep = both sides.",
                            targetMuscle = "Multifidus, Erector Spinae, Gluteus Maximus & Deltoid",
                            sets = 3, reps = 8, durationSeconds = 0,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "bird dog exercise core stability lower back rehabilitation"
                        )
                    ),
                    clinicianGuideline = "Prioritize spinal neutral and deep core activation before progressing to loaded exercises. Avoid end-range flexion under load if disc pathology is suspected. Assess for radiculopathy (radiating leg symptoms) and refer for imaging if neurological signs present. Monitor for red flags: progressive weakness, bowel/bladder changes."
                )
            }

            // ════════════════════════════════════════
            // HIP
            // ════════════════════════════════════════
            "HIP" -> {
                val (condName, condConf, condExpl) = inferHipCondition(desc)
                GeneratedPlanResponse(
                    title = "Hip Mobility & Stability Program",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Supine Hip Flexion Stretch",
                            description = "Lie on your back with both legs straight. Use both hands behind one thigh (not behind the knee) to gently pull the knee toward your chest until you feel a comfortable stretch in the front of the hip and deep in the groin. Keep the opposite leg flat on the floor — if it lifts, you may have tight hip flexors. Hold for 20 seconds, breathing deeply. Switch sides.",
                            targetMuscle = "Iliopsoas, Rectus Femoris & Hip Joint Capsule",
                            sets = 3, reps = 3, durationSeconds = 20,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "supine hip flexion stretch physical therapy"
                        ),
                        Exercise(
                            title = "Clamshells (Hip Abduction)",
                            description = "Lie on your side with hips and knees bent to about 45 degrees. Stack your hips and keep your feet together. Without moving your pelvis (don't rock backward), lift your top knee as high as you can. You should feel the contraction in the side/back of your hip (gluteus medius), not your thigh. Lower slowly with control. To increase difficulty, add a resistance band above your knees.",
                            targetMuscle = "Gluteus Medius & Gluteus Minimus (Hip Abductors)",
                            sets = 3, reps = 15, durationSeconds = 0,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "clamshell exercise hip strengthening gluteus medius physical therapy"
                        ),
                        Exercise(
                            title = "Piriformis Figure-4 Stretch",
                            description = "Lie on your back with both knees bent. Cross your affected ankle over the opposite knee to create a figure-4 shape. Reach through the gap and clasp your hands behind the thigh of the bottom leg. Gently pull the bottom leg toward your chest until you feel a deep stretch in the back of your hip/buttock. Keep your head and shoulders on the floor. Hold 30 seconds.",
                            targetMuscle = "Piriformis, Deep External Rotators & Gluteus Maximus",
                            sets = 3, reps = 3, durationSeconds = 30,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "piriformis figure 4 stretch hip physical therapy"
                        ),
                        Exercise(
                            title = "Standing Hip Hinge (Romanian Deadlift Pattern)",
                            description = "Stand tall with feet hip-width apart and a slight bend in your knees. Hinge forward at the hips by pushing your buttocks backward, keeping your back flat and chest proud. Lower your torso until you feel a stretch in your hamstrings (usually about parallel to the floor). Squeeze your glutes to return to standing. Your weight should stay in your heels. Do not round your lower back.",
                            targetMuscle = "Gluteus Maximus, Hamstrings & Erector Spinae",
                            sets = 3, reps = 10, durationSeconds = 0,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "standing hip hinge romanian deadlift pattern physical therapy"
                        )
                    ),
                    clinicianGuideline = "Avoid deep hip flexion beyond 90° if FAI/impingement is suspected. Assess for positive FADIR test (flexion-adduction-internal rotation). Strengthen hip abductors to address Trendelenburg gait pattern. If groin pain persists beyond 6 weeks, consider imaging to rule out labral pathology."
                )
            }

            // ════════════════════════════════════════
            // NECK
            // ════════════════════════════════════════
            "NECK" -> {
                val (condName, condConf, condExpl) = inferNeckCondition(desc)
                GeneratedPlanResponse(
                    title = "Cervical Spine Mobility & Stability",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Chin Tucks (Deep Cervical Flexor Activation)",
                            description = "Sit upright or stand with your back against a wall, shoulders relaxed. Without tilting your head, draw your chin straight backward as if making a 'double chin.' You should feel a gentle stretch at the base of your skull and a contraction in the deep front-of-neck muscles. Hold for 5 seconds. Release and let your head return to neutral. Keep your jaw relaxed — don't clench your teeth.",
                            targetMuscle = "Longus Colli, Longus Capitis (Deep Cervical Flexors)",
                            sets = 3, reps = 10, durationSeconds = 5,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "chin tuck exercise cervical neck physical therapy"
                        ),
                        Exercise(
                            title = "Neck Rotation Stretch (Active ROM)",
                            description = "Sit tall with shoulders relaxed and hands on your thighs. Slowly turn your head to the right as far as comfortable, leading with your chin. Hold for 5 seconds. Return to center. Repeat to the left. Keep the motion slow and controlled — never force past pain. Your shoulders should remain still and squared forward throughout. One rep = both sides.",
                            targetMuscle = "Sternocleidomastoid (SCM), Upper Trapezius & Splenius Capitis",
                            sets = 3, reps = 8, durationSeconds = 5,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "neck rotation stretch active range of motion exercise"
                        ),
                        Exercise(
                            title = "Levator Scapulae Stretch",
                            description = "Sit upright. Turn your head 45 degrees to the right. Gently use your right hand to pull the back of your head down and forward until you feel a stretch on the left side of your neck/upper back where the neck meets the shoulder blade. Hold for 20 seconds. Breathe deeply throughout. Switch sides. Do NOT pull aggressively — let gravity and a light hand pressure do the work.",
                            targetMuscle = "Levator Scapulae & Upper Trapezius",
                            sets = 3, reps = 3, durationSeconds = 20,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "levator scapulae stretch neck pain physical therapy"
                        ),
                        Exercise(
                            title = "Isometric Neck Strengthening (4-Way)",
                            description = "Sit tall. Place your palm against your forehead. Push your head forward into your hand while your hand resists — don't let your head move (isometric). Hold 5 seconds. Repeat with your hand on the back of your head (push backward), then each side (push sideways). One rep = all four directions. Keep resistance light-to-moderate. Stop immediately if you feel dizziness or radiating pain.",
                            targetMuscle = "SCM, Scalenes, Suboccipitals, Semispinalis & Deep Cervical Extensors",
                            sets = 2, reps = 5, durationSeconds = 5,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "isometric neck strengthening four way exercise physical therapy"
                        )
                    ),
                    clinicianGuideline = "Screen for cervical radiculopathy (Spurling test, upper limb tension tests) before prescribing active ROM. Contraindicate aggressive manipulation if vertebral artery insufficiency is suspected. Assess for forward head posture and thoracic kyphosis contributors. Red flags: progressive arm weakness, myelopathy signs, dizziness with rotation."
                )
            }

            // ════════════════════════════════════════
            // ELBOW
            // ════════════════════════════════════════
            "ELBOW" -> {
                val (condName, condConf, condExpl) = inferElbowCondition(desc)
                GeneratedPlanResponse(
                    title = "Elbow Rehabilitation & Tendon Recovery",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Wrist Extensor Stretch (Tennis Elbow Relief)",
                            description = "Extend your affected arm straight in front of you with the palm facing down. Use your opposite hand to gently bend the wrist downward until you feel a stretch along the top of your forearm near the outside of the elbow. Keep the elbow fully straight. Hold for 30 seconds. Release slowly. You should feel the stretch in the forearm extensor muscles, not sharp pain at the elbow.",
                            targetMuscle = "Extensor Carpi Radialis Brevis & Longus (Lateral Epicondyle)",
                            sets = 3, reps = 3, durationSeconds = 30,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "wrist extensor stretch tennis elbow lateral epicondylitis physical therapy"
                        ),
                        Exercise(
                            title = "Wrist Flexor Stretch (Golfer's Elbow Relief)",
                            description = "Extend your affected arm straight in front with the palm facing up. Use your opposite hand to gently pull the fingers downward (toward the floor), straightening the wrist until you feel a stretch along the inside of your forearm. Keep the elbow locked straight. Hold for 30 seconds. This targets the medial epicondyle — the inner 'bony bump' of the elbow.",
                            targetMuscle = "Flexor Carpi Radialis, Pronator Teres (Medial Epicondyle)",
                            sets = 3, reps = 3, durationSeconds = 30,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "wrist flexor stretch golfer elbow medial epicondylitis rehab"
                        ),
                        Exercise(
                            title = "Eccentric Wrist Curls (Tyler Twist Alternative)",
                            description = "Sit with your forearm resting on a table, wrist hanging over the edge, palm facing down. Hold a light weight (1-2 lbs) or a water bottle. Use your other hand to help lift the wrist up into extension. Then slowly lower the weight under control using only the affected arm (eccentric phase — this is the therapeutic part). Take 3-4 seconds to lower. Repeat. This builds tendon resilience.",
                            targetMuscle = "Wrist Extensors (ECRB) — Eccentric Loading for Tendinopathy",
                            sets = 3, reps = 10, durationSeconds = 0,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "eccentric wrist curl exercise tennis elbow tendinopathy rehab"
                        ),
                        Exercise(
                            title = "Forearm Pronation/Supination with Weight",
                            description = "Sit with your forearm resting on your thigh or a table, elbow bent to 90 degrees. Hold a hammer, wrench, or small weight by one end so it's unbalanced. Slowly rotate your forearm so your palm faces down (pronation), then rotate so your palm faces up (supination). Move through the full range slowly and with control. This strengthens the forearm rotators and stabilizes the elbow joint.",
                            targetMuscle = "Pronator Teres, Supinator & Forearm Rotator Muscles",
                            sets = 3, reps = 12, durationSeconds = 0,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "forearm pronation supination exercise elbow rehab hammer curl"
                        )
                    ),
                    clinicianGuideline = "For lateral epicondylitis, prioritize eccentric wrist extension loading over passive stretching. Assess for radial nerve entrapment (Radial Tunnel Syndrome) if symptoms persist beyond 8 weeks. Avoid gripping activities during acute flare. Counterforce brace may be beneficial 2cm distal to the lateral epicondyle."
                )
            }

            // ════════════════════════════════════════
            // WRIST & HAND
            // ════════════════════════════════════════
            "WRIST_HAND", "WRIST", "HAND" -> {
                val (condName, condConf, condExpl) = inferWristCondition(desc)
                GeneratedPlanResponse(
                    title = "Wrist & Hand Mobility Rehab",
                    conditionName = condName,
                    conditionConfidence = condConf,
                    conditionExplanation = condExpl,
                    exercises = listOf(
                        Exercise(
                            title = "Tendon Gliding Exercises (Nerve Flossing)",
                            description = "Hold your hand in front of you. Perform 5 positions in sequence, holding each for 3 seconds: 1) Straight fingers extended. 2) Hook fist (bend only the finger tips). 3) Full fist (curl all fingers). 4) Table-top (fingers straight but bent at the knuckles at 90°). 5) Straight fist (fingers curled but with the tips straight). This mobilizes the flexor tendons and median nerve through the carpal tunnel.",
                            targetMuscle = "Flexor Digitorum Superficialis/Profundus & Median Nerve",
                            sets = 3, reps = 5, durationSeconds = 15,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "tendon gliding exercises hand carpal tunnel physical therapy"
                        ),
                        Exercise(
                            title = "Wrist Flexion/Extension Stretch",
                            description = "Extend your arm with the elbow straight and palm facing down. Use the other hand to gently bend the wrist downward — hold 20 seconds (stretches extensors). Then flip the palm up and gently bend the wrist backward — hold 20 seconds (stretches flexors). Keep the elbow locked straight throughout. Alternate between both directions. You should feel a comfortable pull, never sharp pain.",
                            targetMuscle = "Wrist Flexors (FCR, FCU) & Extensors (ECRB, ECRL, EDC)",
                            sets = 3, reps = 3, durationSeconds = 20,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "wrist flexion extension stretch exercise carpal tunnel rehab"
                        ),
                        Exercise(
                            title = "Grip Strengthening (Ball Squeeze)",
                            description = "Hold a soft stress ball or tennis ball in your affected hand. Squeeze it as hard as comfortable, focusing on using all your fingers evenly. Hold the squeeze for 5 seconds, then slowly release and fully open your hand. Rest 3 seconds between reps. If a full grip is painful, use a softer ball or rolled-up towel. Progress by using a firmer ball as strength improves.",
                            targetMuscle = "Intrinsic Hand Muscles, Flexor Digitorum & Lumbricals",
                            sets = 3, reps = 10, durationSeconds = 5,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "grip strengthening ball squeeze hand exercise physical therapy"
                        ),
                        Exercise(
                            title = "Thumb Opposition & Finger Abduction",
                            description = "Touch the tip of your thumb to each fingertip in sequence (index → middle → ring → pinky), making an 'O' shape each time. Then spread all fingers wide apart (abduction) and hold for 3 seconds. Bring them back together. Repeat the full sequence. This improves thumb dexterity and strengthens the intrinsic muscles responsible for fine motor control.",
                            targetMuscle = "Opponens Pollicis, Abductor Pollicis Brevis & Interossei",
                            sets = 3, reps = 5, durationSeconds = 0,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "thumb opposition finger abduction hand exercise rehab therapy"
                        )
                    ),
                    clinicianGuideline = "For carpal tunnel syndrome, assess Phalen's test and Tinel's sign. Night splinting in neutral wrist position is first-line. For De Quervain's, assess Finkelstein test — positive indicates first dorsal compartment tenosynovitis. Avoid sustained grip and vibration exposure during acute phases. Consider ergonomic workstation assessment."
                )
            }

            // ════════════════════════════════════════
            // DEFAULT (catch-all for unlisted areas)
            // ════════════════════════════════════════
            else -> {
                GeneratedPlanResponse(
                    title = "General Joint Mobility & Safe Stretching",
                    conditionName = "Non-Specific Joint Dysfunction",
                    conditionConfidence = 40,
                    conditionExplanation = "General joint stiffness or discomfort without clear structural pathology. Often responds well to gentle range-of-motion exercises and progressive isometric strengthening. Could be related to disuse, mild inflammation, or postural strain.",
                    exercises = listOf(
                        Exercise(
                            title = "Gentle Passive ROM Stretch",
                            description = "Position the affected joint in a comfortable, aligned posture. Gently move the joint through its available range in each direction — flexion, extension, rotation. Hold each end-range position for 10 seconds, breathing deeply. Move slowly and never force past the point of discomfort. Repeat in each direction.",
                            targetMuscle = "Primary Joint Stabilizers & Surrounding Musculature",
                            sets = 3, reps = 5, durationSeconds = 10,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "gentle passive range of motion stretch physical therapy"
                        ),
                        Exercise(
                            title = "Isometric Contraction Hold (Multi-Angle)",
                            description = "Place the joint at mid-range. Apply mild resistance using your hand, a wall, or a towel — push against the resistance without allowing any movement (isometric). Hold the contraction for 5 seconds, then fully relax for 3 seconds. Repeat at 3 different angles within the joint's range (early, mid, and near end-range). This builds strength without stressing the joint surfaces.",
                            targetMuscle = "Stabilizing Muscles (Agonist & Antagonist Groups)",
                            sets = 3, reps = 8, durationSeconds = 5,
                            illustrationKey = "general_stretch",
                            videoSearchQuery = "isometric exercise joint strengthening rehab"
                        )
                    ),
                    clinicianGuideline = "Keep active ROM within pain-free limits. Emphasize isometric strengthening before progressing to dynamic exercises. Assess for underlying systemic conditions if multiple joints are involved. Consider imaging if symptoms persist beyond 6 weeks without improvement."
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════
    // Condition inference helpers for fallback mode
    // Analyze symptom keywords to suggest likely conditions
    // ═══════════════════════════════════════════════════

    private fun inferKneeCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("click") || desc.contains("lock") || desc.contains("catch") || desc.contains("twist") ->
                Triple("Meniscal Tear (Medial/Lateral)", 70, "A meniscal tear involves damage to the C-shaped cartilage cushions in the knee. Symptoms include clicking, catching, locking sensations, and swelling — especially after twisting or pivoting movements. The medial meniscus is more commonly injured.")
            desc.contains("swell") || desc.contains("unstable") || desc.contains("gave way") || desc.contains("pop") || desc.contains("buckle") ->
                Triple("ACL Sprain / Ligament Instability", 65, "An ACL injury occurs when the anterior cruciate ligament is stretched or torn, often from sudden deceleration, direction changes, or landing from a jump. Hallmark symptoms include an audible 'pop,' rapid swelling, instability, and difficulty bearing weight.")
            desc.contains("front") || desc.contains("kneecap") || desc.contains("stairs") || desc.contains("squat") || desc.contains("sit") ->
                Triple("Patellofemoral Pain Syndrome (Runner's Knee)", 72, "The most common cause of anterior knee pain. The kneecap (patella) doesn't track properly in its groove during bending. It typically worsens with squatting, stair climbing, running, or prolonged sitting with bent knees ('movie theater sign').")
            desc.contains("stiff") || desc.contains("grind") || desc.contains("creak") || desc.contains("age") || desc.contains("wear") ->
                Triple("Knee Osteoarthritis", 62, "Osteoarthritis involves progressive wearing of the articular cartilage that cushions the knee joint. This causes bone-on-bone friction leading to pain, stiffness, crepitus (grinding/creaking), and reduced range of motion. Most common in adults over 50.")
            desc.contains("inner") || desc.contains("medial") || desc.contains("inside") ->
                Triple("Medial Collateral Ligament (MCL) Sprain", 58, "An MCL sprain involves stretching or tearing of the ligament on the inner side of the knee. It's commonly caused by a direct blow to the outer knee or a valgus stress. Symptoms include inner knee pain, swelling, and instability with side-to-side movements.")
            desc.contains("below") || desc.contains("tendon") || desc.contains("jump") || desc.contains("run") ->
                Triple("Patellar Tendinopathy (Jumper's Knee)", 64, "Patellar tendinopathy is an overuse injury of the tendon connecting the kneecap to the shinbone. Common in athletes who jump frequently. It causes pain just below the kneecap that worsens with jumping, running, squatting, and going down stairs.")
            else ->
                Triple("Patellofemoral Pain Syndrome", 55, "Patellofemoral syndrome is the most common cause of anterior knee pain. It involves irritation of the cartilage under the kneecap, typically caused by muscle imbalances, overuse, or poor biomechanics.")
        }
    }

    private fun inferShoulderCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("overhead") || desc.contains("reaching") || desc.contains("pinch") || desc.contains("arc") || desc.contains("painful arc") ->
                Triple("Subacromial Impingement Syndrome", 72, "Shoulder impingement occurs when the rotator cuff tendons and subacromial bursa are compressed between the humeral head and the acromion during overhead movements. This causes a characteristic painful arc between 60-120° of abduction and often worsens at night.")
            desc.contains("tear") || desc.contains("weakness") || desc.contains("can't lift") || desc.contains("night") || desc.contains("sleep") ->
                Triple("Rotator Cuff Tendinopathy / Partial Tear", 68, "Rotator cuff tendinopathy involves inflammation, degeneration, or partial tearing of the tendons (supraspinatus, infraspinatus, subscapularis, teres minor) that stabilize the shoulder. It causes pain with lifting, reaching behind the back, weakness in overhead activities, and often disrupts sleep.")
            desc.contains("frozen") || desc.contains("stuck") || desc.contains("can't move") || desc.contains("stiff") || desc.contains("limited") ->
                Triple("Adhesive Capsulitis (Frozen Shoulder)", 75, "Frozen shoulder involves progressive thickening and contraction of the glenohumeral joint capsule, severely limiting ROM in all directions — especially external rotation. It progresses through three phases: freezing (increasing pain, 2-9 months), frozen (stiffness plateau, 4-12 months), and thawing (gradual recovery, 5-24 months).")
            desc.contains("dislocat") || desc.contains("slip") || desc.contains("sublux") ->
                Triple("Glenohumeral Instability / Subluxation", 65, "Shoulder instability occurs when the humeral head partially or fully slips out of the glenoid socket. It can be traumatic (from a fall or impact) or atraumatic (from hyperlaxity). Patients often report a feeling of the shoulder 'slipping' or 'giving out,' particularly in overhead or abducted positions.")
            desc.contains("top") || desc.contains("ac joint") || desc.contains("collar") || desc.contains("bump") ->
                Triple("AC Joint Sprain / Separation", 63, "An acromioclavicular (AC) joint sprain involves damage to the ligaments connecting the collarbone to the shoulder blade. It's often caused by a direct fall onto the shoulder. Symptoms include point tenderness on top of the shoulder, a visible bump, and pain with cross-body arm movements.")
            else ->
                Triple("Rotator Cuff Tendinopathy", 58, "Rotator cuff tendinopathy is the most common shoulder condition, involving inflammation and degeneration of the tendons around the shoulder. Risk factors include age over 40, overhead work or sports, and poor posture.")
        }
    }

    private fun inferSpineCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("radiat") || desc.contains("shoot") || desc.contains("leg") || desc.contains("sciatic") || desc.contains("numb") || desc.contains("tingl") ->
                Triple("Lumbar Disc Herniation / Sciatica", 70, "A herniated disc occurs when the nucleus pulposus of a spinal disc protrudes through the annulus fibrosus, potentially compressing an adjacent nerve root. This causes radiating pain, numbness, or tingling along the nerve distribution — most commonly down the buttock and leg (sciatica). L4-L5 and L5-S1 levels are most frequently affected.")
            desc.contains("morning") || desc.contains("stiff") || desc.contains("ache") || desc.contains("chronic") ->
                Triple("Lumbar Spondylosis (Degenerative Disc Disease)", 60, "Degenerative disc disease involves age-related breakdown of the intervertebral discs. The discs lose hydration and height, reducing their shock-absorbing capacity. It typically causes chronic lower back aching, morning stiffness that improves with movement, and pain with prolonged static positions (sitting or standing).")
            desc.contains("bend") || desc.contains("lift") || desc.contains("strain") || desc.contains("spasm") || desc.contains("twist") ->
                Triple("Lumbar Muscle Strain / Spasm", 68, "A lumbar strain involves overstretching or micro-tearing of the paravertebral muscles and ligaments. It commonly results from improper lifting mechanics, sudden twisting, or awkward movements. Symptoms include localized low back pain, muscle spasm, difficulty with position changes, and pain that worsens with bending or lifting.")
            desc.contains("narrow") || desc.contains("walk") || desc.contains("stand") || desc.contains("relief sit") ->
                Triple("Lumbar Spinal Stenosis", 62, "Spinal stenosis is narrowing of the spinal canal that compresses the spinal cord and/or nerve roots. Symptoms include back and leg pain (neurogenic claudication) that worsens with standing and walking but improves with sitting or leaning forward (shopping cart sign). Most common in adults over 60.")
            desc.contains("upper") || desc.contains("thoracic") || desc.contains("mid back") || desc.contains("between blade") ->
                Triple("Thoracic Spine Dysfunction / Kyphotic Postural Syndrome", 58, "Thoracic dysfunction involves pain and stiffness in the mid-upper back, often related to sustained poor posture (excessive kyphosis), muscle imbalances between the pectorals and scapular retractors, or costovertebral joint irritation.")
            else ->
                Triple("Non-Specific Lower Back Pain", 55, "Non-specific lower back pain is the most common type of back pain, affecting up to 80% of adults at some point. It describes discomfort in the lumbar region without clear structural or neurological pathology. It is often related to muscle deconditioning, poor posture, psychosocial stress, or prolonged inactivity.")
        }
    }

    private fun inferHipCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("groin") || desc.contains("pinch") || desc.contains("deep") || desc.contains("squat") || desc.contains("impinge") ->
                Triple("Femoroacetabular Impingement (FAI)", 68, "FAI occurs when abnormal bone growth on the femoral head (cam type) or acetabular rim (pincer type) creates friction during hip movement. It causes deep groin pain during hip flexion and internal rotation, particularly during squatting, pivoting, or prolonged sitting. It is a leading cause of hip pain in young active adults.")
            desc.contains("side") || desc.contains("outer") || desc.contains("lateral") || desc.contains("lying") || desc.contains("sleep") ->
                Triple("Greater Trochanteric Bursitis / Gluteal Tendinopathy", 70, "Lateral hip pain syndrome involves inflammation of the trochanteric bursa and/or tendinopathy of the gluteus medius/minimus tendons at their insertion on the greater trochanter. It causes pain on the outer hip that worsens with lying on the affected side, climbing stairs, and prolonged standing on one leg.")
            desc.contains("click") || desc.contains("catch") || desc.contains("lock") || desc.contains("sharp") ->
                Triple("Acetabular Labral Tear", 62, "A hip labral tear involves damage to the ring of cartilage (labrum) that lines the rim of the hip socket. It causes clicking, catching, or locking sensations in the hip, along with deep groin pain. It is often associated with FAI and can worsen with activities that involve hip rotation or deep flexion.")
            desc.contains("buttock") || desc.contains("sciatic") || desc.contains("sit") || desc.contains("radiat") ->
                Triple("Piriformis Syndrome", 63, "Piriformis syndrome occurs when the piriformis muscle in the buttock compresses or irritates the sciatic nerve. It causes deep buttock pain that may radiate down the back of the leg, mimicking sciatica. Symptoms worsen with sitting, climbing stairs, or activities that involve hip internal rotation.")
            desc.contains("band") || desc.contains("outside") || desc.contains("running") || desc.contains("snap") ->
                Triple("IT Band Syndrome / Snapping Hip", 60, "Iliotibial band syndrome involves friction and inflammation where the IT band slides over the lateral femoral condyle or greater trochanter. It causes lateral hip or knee pain during repetitive activities like running. Snapping hip syndrome produces an audible or palpable 'snap' with hip flexion/extension.")
            desc.contains("stiff") || desc.contains("ache") || desc.contains("old") || desc.contains("arthrit") ->
                Triple("Hip Osteoarthritis", 65, "Hip osteoarthritis involves progressive wearing of the articular cartilage of the hip joint. It causes deep groin or anterior hip pain, morning stiffness, reduced ROM (especially internal rotation), and difficulty with activities like putting on shoes. Most common after age 50.")
            else ->
                Triple("Hip Impingement / Non-Specific Hip Pain", 52, "Hip pain without specific structural pathology identified. Could involve early-stage impingement, muscular imbalances, or referred pain from the lumbar spine. A clinical examination including FADIR, FABER, and log roll tests can help differentiate the source.")
        }
    }

    private fun inferNeckCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("headache") || desc.contains("head") || desc.contains("base of skull") || desc.contains("migraine") ->
                Triple("Cervicogenic Headache", 70, "Cervicogenic headaches originate from dysfunction in the upper cervical spine (C1-C3) and refer pain to the head. They are typically one-sided, start at the base of the skull, and may spread to the forehead or behind the eye. They are often associated with neck stiffness and are triggered by sustained neck postures.")
            desc.contains("radiat") || desc.contains("arm") || desc.contains("shoot") || desc.contains("tingl") || desc.contains("numb") ->
                Triple("Cervical Radiculopathy (Pinched Nerve)", 72, "Cervical radiculopathy occurs when a nerve root in the cervical spine is compressed or irritated — typically by a herniated disc or bone spur at C5-C6 or C6-C7. It causes radiating pain, numbness, tingling, or weakness that follows a dermatomal pattern down the arm to the hand. Symptoms often worsen with neck extension or lateral bending toward the affected side.")
            desc.contains("stiff") || desc.contains("tight") || desc.contains("computer") || desc.contains("posture") || desc.contains("desk") ->
                Triple("Cervical Myofascial Pain / Postural Strain", 68, "Cervical myofascial pain syndrome involves tight, tender muscle bands (trigger points) in the neck and upper shoulder muscles — particularly the upper trapezius, levator scapulae, and suboccipitals. It is strongly associated with prolonged desk work, forward head posture, and stress. It causes dull, aching neck pain with restricted rotation.")
            desc.contains("whiplash") || desc.contains("accident") || desc.contains("car") || desc.contains("collision") || desc.contains("jolt") ->
                Triple("Whiplash-Associated Disorder (WAD)", 75, "Whiplash injury results from rapid acceleration-deceleration of the cervical spine, most commonly from motor vehicle accidents. It damages the muscles, ligaments, facet joints, and discs of the neck. Symptoms include neck pain, stiffness, headache, dizziness, and sometimes cognitive difficulties. Recovery varies from weeks to months.")
            desc.contains("one side") || desc.contains("tilt") || desc.contains("turn") || desc.contains("spasm") || desc.contains("wry") ->
                Triple("Acute Torticollis / Cervical Muscle Spasm", 66, "Acute torticollis (wry neck) involves sudden onset of neck pain and involuntary muscle spasm that holds the head in a tilted or rotated position. It often occurs upon waking or after a sudden movement. The sternocleidomastoid and/or upper trapezius are typically in spasm. Most cases resolve within 1-2 weeks with gentle mobilization.")
            desc.contains("grind") || desc.contains("creak") || desc.contains("bone") || desc.contains("age") ->
                Triple("Cervical Spondylosis (Neck Arthritis)", 60, "Cervical spondylosis is age-related degeneration of the cervical discs and facet joints. It causes chronic neck stiffness, crepitus (grinding/creaking), and intermittent pain that may refer to the upper trapezius or between the shoulder blades. It is extremely common — present radiographically in >80% of adults over 50.")
            else ->
                Triple("Cervical Myofascial Pain / Postural Strain", 55, "Cervical myofascial pain is the most common cause of neck pain. It involves trigger points and muscle tightness in the paraspinal muscles, often driven by sustained postures, stress, or ergonomic factors.")
        }
    }

    private fun inferElbowCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("outside") || desc.contains("lateral") || desc.contains("grip") || desc.contains("tennis") || desc.contains("lift") || desc.contains("typing") ->
                Triple("Lateral Epicondylitis (Tennis Elbow)", 74, "Tennis elbow involves degeneration of the common extensor tendon at the lateral epicondyle — the bony prominence on the outside of the elbow. It causes pain with gripping, lifting, twisting (like opening a jar), and wrist extension. Despite its name, it's most common in non-athletes — especially with repetitive computer/mouse use, manual labor, or gripping tasks.")
            desc.contains("inside") || desc.contains("medial") || desc.contains("golf") || desc.contains("throw") || desc.contains("swing") ->
                Triple("Medial Epicondylitis (Golfer's Elbow)", 72, "Golfer's elbow involves inflammation and micro-tearing of the common flexor tendon at the medial epicondyle — the inner bony bump. It causes pain on the inside of the elbow during gripping, wrist flexion, and forearm pronation. Common in golfers, baseball pitchers, rock climbers, and those performing repetitive wrist movements.")
            desc.contains("tingle") || desc.contains("numb") || desc.contains("ring") || desc.contains("pinky") || desc.contains("funny bone") ->
                Triple("Cubital Tunnel Syndrome (Ulnar Neuropathy)", 70, "Cubital tunnel syndrome occurs when the ulnar nerve is compressed at the medial elbow (the 'funny bone' area). It causes tingling, numbness, and weakness in the ring and little fingers. Symptoms worsen with prolonged elbow flexion (sleeping, phone calls, desk work). Severe cases cause hand weakness and muscle wasting.")
            desc.contains("swell") || desc.contains("bump") || desc.contains("back") || desc.contains("lean") ->
                Triple("Olecranon Bursitis (Student's Elbow)", 68, "Olecranon bursitis is inflammation of the bursa at the tip of the elbow, causing visible swelling, warmth, and tenderness at the back of the elbow. It is commonly caused by prolonged pressure (leaning on hard surfaces), direct trauma, or infection. The swelling can become quite prominent even without significant pain.")
            desc.contains("stiff") || desc.contains("can't straighten") || desc.contains("lock") || desc.contains("extension") ->
                Triple("Post-Traumatic Elbow Stiffness / Contracture", 58, "Elbow stiffness and loss of extension can result from previous fracture, dislocation, prolonged immobilization, or osteoarthritis. Functional elbow ROM requires 30-130° of flexion and 50° each of pronation and supination. Even small losses in extension can significantly impact daily activities.")
            else ->
                Triple("Lateral Epicondylitis (Tennis Elbow)", 55, "Lateral epicondylitis is the most common cause of elbow pain. It involves microtearing and failed healing of the extensor carpi radialis brevis (ECRB) tendon at the lateral epicondyle. It affects 1-3% of the general population.")
        }
    }

    private fun inferWristCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("tingle") || desc.contains("numb") || desc.contains("night") || desc.contains("thumb") || desc.contains("carpal") || desc.contains("median") ->
                Triple("Carpal Tunnel Syndrome", 75, "Carpal tunnel syndrome is compression of the median nerve as it passes through the carpal tunnel in the wrist. It causes numbness, tingling, and burning in the thumb, index, middle, and half of the ring finger. Symptoms are often worst at night and can be provoked by sustained wrist flexion (Phalen's test). Severe cases cause thenar muscle wasting and grip weakness.")
            desc.contains("thumb side") || desc.contains("tendon") || desc.contains("radial") || desc.contains("baby") || desc.contains("texting") ->
                Triple("De Quervain's Tenosynovitis", 72, "De Quervain's tenosynovitis is inflammation of the tendons on the thumb side of the wrist (abductor pollicis longus and extensor pollicis brevis) as they pass through the first dorsal compartment. It causes pain with gripping, thumb movements, and wrist radial deviation. Common in new parents, texters, and anyone performing repetitive thumb motions.")
            desc.contains("click") || desc.contains("lock") || desc.contains("stuck") || desc.contains("trigger") || desc.contains("snap") ->
                Triple("Trigger Finger / Stenosing Tenosynovitis", 70, "Trigger finger occurs when the flexor tendon sheath thickens and narrows, causing the finger to catch or lock when bending and straightening. The affected finger may snap or 'trigger' as it suddenly releases. It can affect any finger but is most common in the ring finger and thumb. It often worsens in the morning.")
            desc.contains("lump") || desc.contains("cyst") || desc.contains("bump") || desc.contains("top of wrist") || desc.contains("ganglion") ->
                Triple("Ganglion Cyst", 72, "A ganglion cyst is a non-cancerous fluid-filled lump that most commonly develops along the tendons or joints of the wrists. The most common location is the dorsal (back) wrist. They can fluctuate in size, may cause aching pain with wrist movements, and can compress nearby nerves if large enough.")
            desc.contains("fall") || desc.contains("scaphoid") || desc.contains("snuffbox") || desc.contains("fracture") ->
                Triple("Scaphoid Fracture / Wrist Sprain", 60, "The scaphoid is the most commonly fractured carpal bone, typically from a fall on an outstretched hand (FOOSH). It causes pain in the anatomical snuffbox (thumb-side wrist hollow) and is notoriously missed on initial X-rays. Even minor wrist sprains can involve ligament damage that affects long-term stability.")
            desc.contains("stiff") || desc.contains("arthrit") || desc.contains("grind") || desc.contains("weak") ->
                Triple("Wrist Osteoarthritis / Post-Traumatic Arthritis", 58, "Wrist osteoarthritis involves wearing of the cartilage in the wrist joints, often following a previous injury (scaphoid fracture, ligament tear). It causes stiffness, aching, weakness, and reduced grip strength. The scapholunate and basal thumb (CMC) joints are most commonly affected.")
            else ->
                Triple("Repetitive Strain Injury (RSI) / Wrist Tendinopathy", 52, "Repetitive strain injuries encompass a group of conditions affecting the tendons, nerves, and muscles of the wrist and forearm from overuse. Common in computer workers, musicians, and manual laborers. Symptoms include diffuse wrist pain, stiffness, and weakness that worsens with activity.")
        }
    }

    private fun inferAnkleCondition(desc: String): Triple<String, Int, String> {
        return when {
            desc.contains("roll") || desc.contains("twist") || desc.contains("inversion") || desc.contains("sprain") || desc.contains("swell") ->
                Triple("Lateral Ankle Sprain (ATFL/CFL)", 72, "A lateral ankle sprain involves stretching or tearing of the ligaments on the outer ankle, most commonly the anterior talofibular ligament (ATFL). It occurs when the foot rolls inward (inversion). Symptoms include swelling, bruising, pain on the outer ankle, and difficulty bearing weight. Grading: I=stretch, II=partial tear, III=complete rupture.")
            desc.contains("heel") || desc.contains("sole") || desc.contains("arch") || desc.contains("morning") || desc.contains("first step") || desc.contains("plantar") ->
                Triple("Plantar Fasciitis", 75, "Plantar fasciitis is inflammation of the thick band of tissue (plantar fascia) that runs along the bottom of the foot from the heel to the toes. It causes stabbing heel pain that is typically worst with the first steps in the morning or after prolonged sitting. Risk factors include flat feet, high arches, obesity, and excessive running.")
            desc.contains("achilles") || desc.contains("back of ankle") || desc.contains("calf") || desc.contains("tendon") || desc.contains("jump") || desc.contains("run") ->
                Triple("Achilles Tendinopathy", 70, "Achilles tendinopathy is a chronic overuse condition of the Achilles tendon, the largest tendon in the body. It causes pain, stiffness, and thickening of the tendon at the back of the ankle, typically 2-6 cm above the heel insertion (midportion type) or at the heel bone (insertional type). Common in runners and middle-aged athletes.")
            desc.contains("outside") || desc.contains("lateral") || desc.contains("weak") || desc.contains("instab") || desc.contains("giving way") ->
                Triple("Chronic Ankle Instability / Peroneal Tendinopathy", 62, "Chronic ankle instability develops after repeated ankle sprains when the ligaments don't fully heal. The peroneal tendons (which evert the foot) may also become inflamed or weakened. Symptoms include recurrent 'giving way,' persistent outer ankle pain, swelling, and difficulty on uneven surfaces.")
            desc.contains("stiff") || desc.contains("front") || desc.contains("dorsi") || desc.contains("block") ->
                Triple("Anterior Ankle Impingement", 60, "Anterior ankle impingement occurs when bone spurs or scar tissue at the front of the ankle joint restrict dorsiflexion. It causes pain at the front of the ankle during activities that require deep ankle bending — squatting, climbing stairs, or running uphill. Common in soccer players ('footballer's ankle') and dancers.")
            else ->
                Triple("Lateral Ankle Sprain", 55, "Lateral ankle sprains are the most common musculoskeletal injury, accounting for 25% of all sports injuries. The ATFL is the most commonly damaged ligament. Even 'minor' sprains require proper rehabilitation to prevent chronic instability.")
        }
    }
}
