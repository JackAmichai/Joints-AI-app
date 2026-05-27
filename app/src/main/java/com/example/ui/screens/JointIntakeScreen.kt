package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BodyArea
import com.example.data.model.TriageStatus
import com.example.data.api.JointAnalysisResponse
import com.example.utils.TriageEvaluation
import androidx.compose.ui.graphics.asImageBitmap
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import com.example.ui.theme.*
import com.example.ui.viewmodel.RehabViewModel

// --- Speech Interface ---
interface TextToSpeechState {
    fun speak(text: String)
    fun stop()
}

@Composable
fun rememberTextToSpeech(): TextToSpeechState {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isInitialized by remember { mutableStateOf(false) }
    
    val state = remember {
        object : TextToSpeechState {
            override fun speak(text: String) {
                if (isInitialized) {
                    tts?.let { t ->
                        t.setPitch(1.15f)
                        t.setSpeechRate(0.92f)
                        try {
                            val femaleVoice = t.voices?.firstOrNull { 
                                it.name.contains("female", ignoreCase = true) || 
                                it.name.contains("f-network", ignoreCase = true) 
                            }
                            if (femaleVoice != null) {
                                t.voice = femaleVoice
                            }
                        } catch (e: Exception) {
                            // Use default voice if voice list is inaccessible
                        }
                        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "intake_guide")
                    }
                }
            }
            override fun stop() {
                tts?.stop()
            }
        }
    }
    
    DisposableEffect(context) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
            }
        }
        val instance = TextToSpeech(context, listener)
        tts = instance
        
        onDispose {
            instance.stop()
            instance.shutdown()
        }
    }
    
    return state
}

private fun triggerVoiceover(step: Int, area: BodyArea?, tts: TextToSpeechState) {
    val promptText = when (step) {
        1 -> {
            "Welcome to Joints AI. I am your virtual therapist assistant. Let us begin your profile setup. First, select the physical joint pain location on your screen to identify your custom therapy targets."
        }
        2 -> {
            "Step two. Let us log your subjective pain details. On a scale of one to ten, please rate your pain severity. Next, describe any pinch points, radiating numbness, or clicking in the text area below so we can run our automatic safety check."
        }
        3 -> {
            val selectedJointText = area?.displayName ?: "joint"
            "Step three. We are ready to perform your Vision range of motion scan. What specific movement of your $selectedJointText should we record? And to what target angle? Please select the movement on your screen and drag the target angle slider."
        }
        else -> ""
    }
    if (promptText.isNotEmpty()) {
        tts.speak(promptText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JointIntakeScreen(
    viewModel: RehabViewModel,
    onNavigateBack: () -> Unit,
    onAssessmentCompleted: (Long) -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 3

    val selectedArea by viewModel.selectedBodyArea.collectAsState()
    val intensity by viewModel.painIntensity.collectAsState()
    val description by viewModel.painDescription.collectAsState()
    val romQuestionChecked by viewModel.rangeOfMotionQuestionChecked.collectAsState()
    val romImage by viewModel.romImage.collectAsState()
    val romAnalysis by viewModel.romAnalysisResult.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingRom.collectAsState()
    val triage by viewModel.triageEvaluation.collectAsState()
    val isGenerating by viewModel.isGeneratingPlan.collectAsState()

    var selectedMovement by remember { mutableStateOf("Flexion") }
    var targetAngle by remember { mutableStateOf(90) }
    var avoidPainChecked by remember { mutableStateOf(false) }
    var voiceoverGuidanceEnabled by remember { mutableStateOf(false) }

    val tts = rememberTextToSpeech()

    LaunchedEffect(currentStep, voiceoverGuidanceEnabled) {
        if (voiceoverGuidanceEnabled) {
            triggerVoiceover(currentStep, selectedArea, tts)
        }
    }

    LaunchedEffect(selectedArea) {
        selectedArea?.let {
            val suggestions = getMovementSuggestions(it)
            if (suggestions.isNotEmpty() && suggestions.first() != "Custom") {
                selectedMovement = suggestions.first()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pain Assessment", color = TextTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) {
                            currentStep--
                            tts.stop()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextTitle)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateBg)
            )
        },
        containerColor = SlateBg
    ) { padValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padValues)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Stepper progress with gradient bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalSteps) { idx ->
                        val active = idx + 1 <= currentStep
                        val barColor = animateColorAsState(
                            if (active) ClinicalTeal else SlateBorder,
                            label = "stepColor"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .then(
                                    if (active) Modifier.background(
                                        Brush.horizontalGradient(listOf(ClinicalTeal, ClinicalTealLight))
                                    )
                                    else Modifier.background(barColor.value)
                                )
                        )
                    }
                }

                // Step label
                Text(
                    text = when (currentStep) {
                        1 -> "Step 1 of 3 · Select Pain Area"
                        2 -> "Step 2 of 3 · Describe Symptoms"
                        else -> "Step 3 of 3 · Motion Assessment"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // AI Guidance toggle
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (voiceoverGuidanceEnabled) Icons.Default.VolumeUp else Icons.Default.SettingsVoice,
                                    contentDescription = "Guidance Mode",
                                    tint = if (voiceoverGuidanceEnabled) ClinicalTeal else TextBody,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Voice Guide",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextTitle
                                )
                            }
                            
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateBgElevated)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (!voiceoverGuidanceEnabled) ClinicalTeal else Color.Transparent)
                                        .clickable { 
                                            voiceoverGuidanceEnabled = false 
                                            tts.stop()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Manual",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!voiceoverGuidanceEnabled) TextOnAccent else TextBody
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (voiceoverGuidanceEnabled) ClinicalTeal else Color.Transparent)
                                        .clickable { 
                                            voiceoverGuidanceEnabled = true 
                                            triggerVoiceover(currentStep, selectedArea, tts)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "AI Voice",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (voiceoverGuidanceEnabled) TextOnAccent else TextBody
                                    )
                                }
                            }
                        }
                        
                        if (voiceoverGuidanceEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "🔊 Voice guidance active",
                                    fontSize = 11.sp,
                                    color = ClinicalTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { triggerVoiceover(currentStep, selectedArea, tts) }
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = null, tint = ClinicalTeal, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Replay",
                                        fontSize = 11.sp,
                                        color = ClinicalTeal,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Step content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentStep) {
                        1 -> StepBodyArea(
                            selected = selectedArea,
                            onSelect = { viewModel.selectBodyArea(it) }
                        )
                        2 -> StepPainDetails(
                            intensity = intensity,
                            onIntensityChange = { viewModel.setPainIntensity(it) },
                            description = description,
                            onDescriptionChange = { viewModel.setPainDescription(it) },
                            triage = triage
                        )
                        3 -> StepRangeOfMotion(
                            romImage = romImage,
                            onSimulateRomCamera = {
                                val simulated = createSimulatedJointJointBitmap(selectedArea ?: BodyArea.KNEE)
                                viewModel.setRomImage(simulated)
                            },
                            romAnalysis = romAnalysis,
                            isAnalyzing = isAnalyzing,
                            selectedArea = selectedArea,
                            selectedMovement = selectedMovement,
                            onSelectedMovementChange = { selectedMovement = it },
                            targetAngle = targetAngle,
                            onTargetAngleChange = { targetAngle = it },
                            avoidPainChecked = avoidPainChecked,
                            onAvoidPainCheckedChange = { avoidPainChecked = it },
                            ttsState = tts,
                            voiceoverEnabled = voiceoverGuidanceEnabled
                        )
                    }
                }

                // Bottom CTA
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { 
                                currentStep-- 
                                tts.stop()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SlateBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextBody)
                        ) {
                            Text("Previous", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    val isNextEnabled = when (currentStep) {
                        1 -> selectedArea != null
                        2 -> description.trim().isNotEmpty() && 
                                triage?.status != TriageStatus.SEEK_EMERGENCY
                        3 -> avoidPainChecked && selectedMovement.trim().isNotEmpty()
                        else -> true
                    }

                    Button(
                        onClick = {
                            if (currentStep < totalSteps) {
                                currentStep++
                                tts.stop()
                            } else {
                                tts.stop()
                                // Append motion context to description for AI prompt
                                val targetMotionDetails = "\n\n[Clinical AI Motion Plan]: The patient plans to record the movement '$selectedMovement' aiming for a target flexion limit of $targetAngle°. The patient has pledged to avoid pain as much as possible, executing only up to the threshold of irritation."
                                viewModel.setPainDescription(description + targetMotionDetails)
                                
                                viewModel.submitIntakeAndGeneratePlan(
                                    selectedMovement = selectedMovement,
                                    targetAngle = targetAngle
                                ) { assessmentId ->
                                    onAssessmentCompleted(assessmentId)
                                }
                            }
                        },
                        enabled = isNextEnabled && !isGenerating,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("next_assessment_step_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentStep == totalSteps) GreenSuccess else ClinicalTeal,
                            contentColor = if (currentStep == totalSteps) TextOnAccent else TextOnAccent,
                            disabledContainerColor = SlateBorder,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(color = TextOnAccent, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (currentStep == totalSteps) "Submit to Review" else "Continue",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Stepper Sub Views ---

@Composable
fun StepBodyArea(
    selected: BodyArea?,
    onSelect: (BodyArea) -> Unit
) {
    Column {
        Text(
            text = "Select Joint Pain Area",
            style = MaterialTheme.typography.headlineSmall,
            color = TextTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Identify the region where you're experiencing discomfort or restricted movement.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextBody,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(BodyArea.values()) { area ->
                val isSelected = selected == area
                val borderCol = if (isSelected) ClinicalTeal else SlateBorder
                val bgCol = if (isSelected) ClinicalTealSurface else SlateCard

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable { onSelect(area) }
                        .testTag("body_area_${area.name}"),
                    colors = CardDefaults.cardColors(containerColor = bgCol),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderCol
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = getAreaIcon(area),
                            contentDescription = area.displayName,
                            tint = if (isSelected) ClinicalTealLight else TextBody,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = area.displayName,
                            color = if (isSelected) ClinicalTealLight else TextTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StepPainDetails(
    intensity: Int,
    onIntensityChange: (Int) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    triage: TriageEvaluation?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Pain Severity",
                style = MaterialTheme.typography.titleMedium,
                color = TextTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$intensity/10",
                            fontWeight = FontWeight.Black,
                            color = getSeverityColor(intensity),
                            fontSize = 28.sp
                        )
                        Text(
                            text = getSeverityLabel(intensity),
                            color = getSeverityColor(intensity),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = intensity.toFloat(),
                        onValueChange = { onIntensityChange(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = getSeverityColor(intensity),
                            activeTrackColor = getSeverityColor(intensity),
                            inactiveTrackColor = SlateBorder
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Column {
            Text(
                text = "Describe Your Symptoms",
                style = MaterialTheme.typography.titleMedium,
                color = TextTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Include pain type, triggers, and any numbness or clicking.",
                style = MaterialTheme.typography.bodySmall,
                color = TextBody,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .testTag("pain_description_input"),
                placeholder = { Text("E.g., My shoulder clicks and pinches when raising my arm above shoulder level...", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClinicalTeal,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = TextTitle,
                    unfocusedTextColor = TextTitle,
                    cursorColor = ClinicalTeal,
                    focusedContainerColor = SlateCard,
                    unfocusedContainerColor = SlateCard
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Triage warnings
        val ev = triage
        if (ev != null) {
            AnimatedVisibility(visible = true) {
                val bannerColor = when (ev.status) {
                    TriageStatus.SEEK_EMERGENCY -> CoralAlarm
                    TriageStatus.SEEK_PHYSICIAN -> AmberWarning
                    TriageStatus.PROCEED_WITH_CAUTION -> AmberWarning
                    else -> GreenSuccess
                }
                val surfaceColor = when (ev.status) {
                    TriageStatus.SEEK_EMERGENCY -> CoralAlarmSurface
                    TriageStatus.SEEK_PHYSICIAN -> AmberWarningSurface
                    TriageStatus.PROCEED_WITH_CAUTION -> AmberWarningSurface
                    else -> GreenSuccessSurface
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = when (ev.status) {
                                TriageStatus.SEEK_EMERGENCY -> Icons.Default.Warning
                                TriageStatus.SEEK_PHYSICIAN -> Icons.Default.Warning
                                TriageStatus.PROCEED_WITH_CAUTION -> Icons.Default.Info
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = ev.status.label,
                            tint = bannerColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = ev.status.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTitle,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ev.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextBody,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getMovementSuggestions(area: BodyArea?): List<String> {
    return when (area) {
        BodyArea.NECK -> listOf("Neck Rotation (Left/Right)", "Neck Flexion (Chin to Chest)", "Lateral Flexion (Ear to Shoulder)", "Custom")
        BodyArea.SHOULDER -> listOf("Shoulder Flexion (Raise hand up)", "Shoulder Abduction (Raise hand to side)", "External Rotation", "Custom")
        BodyArea.ELBOW -> listOf("Elbow Flexion (Bend elbow)", "Elbow Extension (Straighten elbow)", "Forearm Supination", "Custom")
        BodyArea.WRIST_HAND -> listOf("Wrist Flexion (Bend palm down)", "Wrist Extension (Wrist up)", "Focal Fist Clench", "Custom")
        BodyArea.SPINE_BACK -> listOf("Lumbar Flexion (Bend forward)", "Lumbar Extension (Bend backward)", "Side Flexion", "Custom")
        BodyArea.HIP -> listOf("Hip Flexion (Knee to chest)", "Hip Abduction (Leg swing outward)", "Hip Extension", "Custom")
        BodyArea.KNEE -> listOf("Knee Flexion (Heel to glute)", "Knee Extension (Straighten leg)", "Custom")
        BodyArea.ANKLE_FOOT -> listOf("Dorsiflexion (Toes upward)", "Plantarflexion (Point toes down)", "Ankle Inversion", "Custom")
        null -> listOf("Flexion", "Extension", "Abduction", "Rotation", "Custom")
    }
}

@Composable
fun StepRangeOfMotion(
    romImage: Bitmap?,
    onSimulateRomCamera: () -> Unit,
    romAnalysis: JointAnalysisResponse?,
    isAnalyzing: Boolean,
    selectedArea: BodyArea?,
    selectedMovement: String,
    onSelectedMovementChange: (String) -> Unit,
    targetAngle: Int,
    onTargetAngleChange: (Int) -> Unit,
    avoidPainChecked: Boolean,
    onAvoidPainCheckedChange: (Boolean) -> Unit,
    ttsState: TextToSpeechState,
    voiceoverEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Range of Motion Scan",
            style = MaterialTheme.typography.headlineSmall,
            color = TextTitle,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Select the movement to record and set your target flexion angle.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextBody
        )

        // Motion planner card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        tint = ClinicalTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Motion Assessment Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextTitle
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Movement selection
                Text(
                    text = "1. Movement to Record",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextTitle
                )
                Spacer(modifier = Modifier.height(8.dp))

                val movements = getMovementSuggestions(selectedArea)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    movements.forEach { m ->
                        val isSel = selectedMovement == m || (m == "Custom" && !movements.dropLast(1).contains(selectedMovement))
                        val chipBg = if (isSel) ClinicalTeal else SlateBgElevated
                        val chipBorder = if (isSel) ClinicalTeal else SlateBorder
                        val chipText = if (isSel) TextOnAccent else TextBody

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBg)
                                .clickable {
                                    if (m == "Custom") {
                                        onSelectedMovementChange("")
                                        ttsState.speak("Please describe your custom joint movement.")
                                    } else {
                                        onSelectedMovementChange(m)
                                        ttsState.speak("You have selected $m.")
                                    }
                                }
                                .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = m,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = chipText
                            )
                        }
                    }
                }

                // Custom input
                val isCustomSelected = !movements.dropLast(1).contains(selectedMovement)
                if (isCustomSelected || selectedMovement.isEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = selectedMovement,
                        onValueChange = onSelectedMovementChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_movement_input"),
                        placeholder = { Text("Describe custom movement...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextTitle,
                            unfocusedTextColor = TextTitle,
                            focusedBorderColor = ClinicalTeal,
                            unfocusedBorderColor = SlateBorder,
                            cursorColor = ClinicalTeal,
                            focusedContainerColor = SlateCard,
                            unfocusedContainerColor = SlateCard
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Angle target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. Target Flexion Angle",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextTitle
                    )
                    Text(
                        text = "$targetAngle°",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTealLight
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = targetAngle.toFloat(),
                    onValueChange = { 
                        onTargetAngleChange(it.toInt()) 
                    },
                    valueRange = 30f..180f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = ClinicalTeal,
                        activeTrackColor = ClinicalTeal,
                        inactiveTrackColor = SlateBorder
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Safety pledge
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CoralAlarmSurface),
                    border = BorderStroke(1.dp, CoralAlarm.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onAvoidPainCheckedChange(!avoidPainChecked)
                                if (!avoidPainChecked) {
                                    ttsState.speak("Safety pledge accepted.")
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = avoidPainChecked,
                            onCheckedChange = { 
                                onAvoidPainCheckedChange(it) 
                                if (it) {
                                    ttsState.speak("Safety pledge accepted.")
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = CoralAlarm,
                                uncheckedColor = TextBody,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ Safety Verification",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralAlarm
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "I will perform this movement slowly and stop before it becomes painful.",
                                fontSize = 11.sp,
                                color = TextBody,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Camera simulation card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (romImage == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Camera",
                            tint = if (avoidPainChecked) TextBody else TextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onSimulateRomCamera,
                            enabled = avoidPainChecked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ClinicalTeal,
                                contentColor = TextOnAccent,
                                disabledContainerColor = SlateBorder,
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("simulate_camera_button")
                        ) {
                            Text("Simulate Joint Camera", fontWeight = FontWeight.SemiBold)
                        }
                        if (!avoidPainChecked) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Accept safety rule to enable camera",
                                fontSize = 11.sp,
                                color = CoralAlarm,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.Image(
                        bitmap = romImage.asImageBitmap(),
                        contentDescription = "Simulated Joint Flexion Angle",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Analysis loading
        if (isAnalyzing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = ClinicalTeal, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Analyzing range of motion...", color = ClinicalTeal, fontSize = 13.sp)
            }
        }

        // ROM results
        val analysis = romAnalysis
        if (analysis != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, ClinicalTeal.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Vision Scanner Results",
                        color = ClinicalTeal,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Estimated Flexion", style = MaterialTheme.typography.bodySmall, color = TextBody)
                            Text(
                                text = "${analysis.estimatedAngle}°",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextTitle,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Restriction", style = MaterialTheme.typography.bodySmall, color = TextBody)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (analysis.restrictionLevel == "None") GreenSuccessSurface else AmberWarningSurface
                            ) {
                                Text(
                                    text = analysis.restrictionLevel.uppercase(),
                                    color = if (analysis.restrictionLevel == "None") GreenSuccess else AmberWarning,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = analysis.analysisFeedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBody,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Disclaimer
        Text(
            text = "Disclaimer: Programs are generated by AI and reviewed by specialists. They do not replace in-person medical counsel.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
    }
}

// --- Domain Helpers ---

private fun getAreaIcon(area: BodyArea): ImageVector {
    return when (area) {
        BodyArea.NECK -> Icons.Default.Face
        BodyArea.SHOULDER -> Icons.Default.Accessibility
        BodyArea.ELBOW -> Icons.Default.FitnessCenter
        BodyArea.WRIST_HAND -> Icons.Default.PanTool
        BodyArea.SPINE_BACK -> Icons.Default.SelfImprovement
        BodyArea.HIP -> Icons.Default.DirectionsRun
        BodyArea.KNEE -> Icons.Default.Healing
        BodyArea.ANKLE_FOOT -> Icons.Default.DirectionsWalk
    }
}

private fun getSeverityLabel(intensity: Int): String {
    return when (intensity) {
        0 -> "No Pain"
        in 1..3 -> "Mild"
        in 4..6 -> "Moderate"
        in 7..9 -> "Severe"
        else -> "Emergency"
    }
}

private fun getSeverityColor(intensity: Int): Color {
    return when (intensity) {
        in 1..3 -> GreenSuccess
        in 4..6 -> AmberWarning
        else -> CoralAlarm
    }
}

/**
 * Creates a simulated joint visualization using custom canvas.
 */
private fun createSimulatedJointJointBitmap(area: BodyArea): Bitmap {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Draw dark background
    canvas.drawColor(SlateCard.toArgb())

    val paint = Paint().apply {
        color = SlateBorder.toArgb()
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    // Draw coordinate grid
    val gridPaint = Paint().apply {
        color = Color(0x15FFFFFF).toArgb()
        strokeWidth = 1f
    }
    for (i in 0..size step 40) {
        canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), gridPaint)
        canvas.drawLine(0f, i.toFloat(), size.toFloat(), i.toFloat(), gridPaint)
    }

    // Draw joint limbs
    val bonePaint = Paint().apply {
        color = TextTitle.toArgb()
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    val accentPaint = Paint().apply {
        color = ClinicalTeal.toArgb()
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    val textPaint = Paint().apply {
        color = ClinicalTealLight.toArgb()
        textSize = 34f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    when (area) {
        BodyArea.KNEE -> {
            canvas.drawLine(150f, 120f, 260f, 260f, bonePaint)
            canvas.drawLine(260f, 260f, 170f, 380f, bonePaint)
            canvas.drawCircle(260f, 260f, 18f, Paint().apply { color = ClinicalTeal.toArgb() })
            canvas.drawArc(220f, 220f, 300f, 300f, 35f, 110f, false, accentPaint)
            canvas.drawText("ROM: 105°", 280f, 230f, textPaint)
        }
        BodyArea.SHOULDER -> {
            canvas.drawLine(200f, 320f, 260f, 220f, bonePaint)
            canvas.drawLine(260f, 220f, 380f, 240f, bonePaint)
            canvas.drawCircle(260f, 220f, 18f, Paint().apply { color = ClinicalTeal.toArgb() })
            canvas.drawArc(220f, 180f, 300f, 260f, 0f, 90f, false, accentPaint)
            canvas.drawText("ROM: 98°", 220f, 140f, textPaint)
        }
        else -> {
            canvas.drawLine(160f, 256f, 256f, 256f, bonePaint)
            canvas.drawLine(256f, 256f, 330f, 180f, bonePaint)
            canvas.drawCircle(256f, 256f, 18f, Paint().apply { color = ClinicalTeal.toArgb() })
            canvas.drawText("ROM: 135°", 280f, 220f, textPaint)
        }
    }

    return bitmap
}
