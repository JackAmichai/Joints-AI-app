package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleFilled
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.example.data.api.ChatMessage
import com.example.data.api.VideoAnalysisResponse
import com.example.data.model.Exercise
import com.example.data.model.ExerciseProgram
import com.example.data.model.ExerciseProgress
import com.example.data.model.UserAssessment
import com.example.ui.theme.*
import com.example.ui.viewmodel.RehabViewModel
import com.example.utils.RecoveryReportGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    viewModel: RehabViewModel,
    onNavigateBack: () -> Unit,
    onEnterClinicianPortalForProgram: (Long) -> Unit
) {
    val programs by viewModel.programs.collectAsState()
    val activeProgram by viewModel.activeProgram.collectAsState()
    val logs by viewModel.progressHistory.collectAsState()
    val assessments by viewModel.assessments.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("My Plan", "AI Video Lab", "AI Chat", "History")

    var playingExercise by remember { mutableStateOf<Exercise?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recovery Hub", color = TextTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextTitle)
                    }
                },
                actions = {
                    // Share / Export PDF
                    IconButton(
                        onClick = {
                            val prog = activeProgram
                            if (prog != null) {
                                val assessment = assessments.find { it.id == prog.assessmentId }
                                RecoveryReportGenerator.generateAndShare(
                                    context = context,
                                    program = prog,
                                    assessment = assessment,
                                    progressLogs = logs
                                )
                            } else {
                                Toast.makeText(context, "No active program to export", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("export_report_action")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Export Report", tint = ClinicalTeal)
                    }
                    IconButton(
                        onClick = { viewModel.clearIntakeForm() },
                        modifier = Modifier.testTag("reset_workflow_action")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = TextBody)
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
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Welcome Back,",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextBody
                        )
                        Text(
                            text = "Joint Recovery",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextTitle
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = ClinicalTealSurface,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DirectionsRun,
                                contentDescription = "Avatar",
                                tint = ClinicalTeal,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = ClinicalTeal,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = ClinicalTeal,
                                height = 3.dp
                            )
                        }
                    },
                    divider = { HorizontalDivider(color = SlateBorder, thickness = 1.dp) },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            unselectedContentColor = TextBody,
                            selectedContentColor = ClinicalTeal
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // MY PLAN TAB
                        if (programs.isEmpty()) {
                            EmptyPlanState()
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                activeProgram?.let { prog ->
                                    item {
                                        ClinicianOversightHeaderCard(
                                            prog = prog,
                                            onSkipReview = {
                                                viewModel.approveProgram(
                                                    programId = prog.id,
                                                    therapistName = "Dr. Sarah Adams, DPT",
                                                    therapistNotes = "Self-approved trial. Plan vetted for general stability drills.",
                                                    reviewedExercises = prog.exercises
                                                )
                                            },
                                            onEnterClinicianReview = {
                                                onEnterClinicianPortalForProgram(prog.id)
                                            }
                                        )
                                    }

                                    item { ProgramSummaryCard(prog = prog) }

                                    // AI Condition Diagnosis Card
                                    if (prog.conditionName != null) {
                                        item {
                                            ConditionDiagnosisCard(
                                                conditionName = prog.conditionName,
                                                confidence = prog.conditionConfidence ?: 50,
                                                explanation = prog.conditionExplanation ?: ""
                                            )
                                        }
                                    }

                                    // Patient Assessment Summary Card
                                    val matchingAssessment = assessments.find { it.id == prog.assessmentId }
                                    if (matchingAssessment != null) {
                                        item {
                                            PatientReportCard(assessment = matchingAssessment)
                                        }
                                    }

                                    item {
                                        Text(
                                            text = "Exercises (${prog.exercises.size})",
                                            fontWeight = FontWeight.Bold,
                                            color = TextTitle,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }

                                    items(prog.exercises, key = { it.title }) { exercise ->
                                        ExerciseDetailCard(
                                            exercise = exercise,
                                            onPlay = { playingExercise = exercise }
                                        )
                                    }

                                    // Export Recovery Report Card
                                    item {
                                        ExportReportCard(
                                            onExport = {
                                                val assessment = assessments.find { it.id == prog.assessmentId }
                                                RecoveryReportGenerator.generateAndShare(
                                                    context = context,
                                                    program = prog,
                                                    assessment = assessment,
                                                    progressLogs = logs
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // VIDEO AI LAB TAB
                        VideoAiLabTab(viewModel = viewModel)
                    }
                    2 -> {
                        // AI CHAT TAB
                        AiChatTab(viewModel = viewModel)
                    }
                    3 -> {
                        // HISTORY TAB
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item { WeeklyActivityChartCard(logs) }

                            item {
                                Text(
                                    text = "Workout History",
                                    fontWeight = FontWeight.Bold,
                                    color = TextTitle,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            if (logs.isEmpty()) {
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = SlateCard,
                                        border = BorderStroke(1.dp, SlateBorder),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "No workouts logged yet. Complete an exercise to start tracking!",
                                            color = TextBody,
                                            modifier = Modifier.padding(24.dp),
                                            textAlign = TextAlign.Center,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                items(logs) { log ->
                                    completedWorkoutRowItem(log)
                                }
                            }
                        }
                    }
                }
            }

            // Exercise player dialog
            playingExercise?.let { exercise ->
                ExercisePlayerDialog(
                    exercise = exercise,
                    programId = activeProgram?.id ?: 0L,
                    onDismiss = { playingExercise = null },
                    onCompleted = { sets, reps, prePain, postPain, note ->
                        viewModel.logWorkout(
                            programId = activeProgram?.id ?: 0L,
                            exerciseTitle = exercise.title,
                            sets = sets,
                            reps = reps,
                            painPre = prePain,
                            painPost = postPain,
                            note = note
                        )
                        playingExercise = null
                    }
                )
            }
        }
    }
}

// ─── AI CHAT TAB ───

@Composable
fun AiChatTab(viewModel: RehabViewModel) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isSending by viewModel.isSendingChat.collectAsState()
    val chatError by viewModel.chatError.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalTealSurface),
            border = BorderStroke(1.dp, ClinicalTeal.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ClinicalTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "AI",
                        tint = ClinicalTealLight,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Rehab Assistant",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextTitle
                    )
                    Text(
                        text = "Ask about your exercises, recovery tips, or pain management",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBody,
                        lineHeight = 16.sp
                    )
                }
                if (chatMessages.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearChat() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear chat",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Messages list
        if (chatMessages.isEmpty()) {
            // Empty state with suggestion chips
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = null,
                    tint = SlateBorder,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Start a Conversation",
                    color = TextTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Ask anything about your recovery",
                    color = TextBody,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Quick suggestion chips
                val suggestions = listOf(
                    "How many sets should I do?",
                    "What if my pain gets worse?",
                    "Can I use a brace?",
                    "How often should I exercise?"
                )
                suggestions.forEach { suggestion ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                inputText = suggestion
                                viewModel.sendChatMessage(suggestion)
                                inputText = ""
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = SlateCard,
                        border = BorderStroke(1.dp, SlateBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ClinicalTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = suggestion,
                                color = TextTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chatMessages) { message ->
                    ChatBubble(message = message)
                }

                // Typing indicator
                if (isSending) {
                    item {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = ClinicalTeal,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Thinking...",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Input bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    placeholder = { Text("Ask about your recovery...", color = TextMuted, fontSize = 14.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextTitle,
                        unfocusedTextColor = TextTitle,
                        cursorColor = ClinicalTeal,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    maxLines = 3,
                    singleLine = false,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isSending) {
                            viewModel.sendChatMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isSending) ClinicalTeal
                            else SlateBorder
                        )
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !isSending) TextOnAccent else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ClinicalTealSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = ClinicalTeal,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isUser) ClinicalTeal else SlateCardElevated,
            border = if (!isUser) BorderStroke(1.dp, SlateBorder) else null
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) TextOnAccent else TextTitle,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MintSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MintSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Sub-Composables ───

@Composable
fun EmptyPlanState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AssignmentLate,
            contentDescription = "Empty",
            tint = SlateBorder,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Active Plan",
            color = TextTitle,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Complete an assessment to generate your personalized rehabilitation program.",
            color = TextBody,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ClinicianOversightHeaderCard(
    prog: ExerciseProgram,
    onSkipReview: () -> Unit,
    onEnterClinicianReview: () -> Unit
) {
    val isPending = prog.clinicianStatus == "PENDING"
    val cardColor = if (isPending) AmberWarningSurface else GreenSuccessSurface
    val borderCol = if (isPending) AmberWarning else GreenSuccess

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderCol.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPending) Icons.Default.HourglassEmpty else Icons.Default.Verified,
                    contentDescription = prog.clinicianStatus,
                    tint = borderCol,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isPending) "Pending Clinical Review" else "Clinician Verified",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPending) "This plan requires validation from a physiotherapist. You can bypass to try exercises or open the review panel."
                       else "Verified by ${prog.therapistName ?: "Dr. Sarah Adams, DPT"}.",
                style = MaterialTheme.typography.bodySmall,
                color = TextBody,
                lineHeight = 16.sp
            )

            if (isPending) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSkipReview,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextBody),
                        border = BorderStroke(1.dp, SlateBorder),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Try Exercises", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onEnterClinicianReview,
                        modifier = Modifier.weight(1.3f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = TextOnAccent),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text("Open Review", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramSummaryCard(prog: ExerciseProgram) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = prog.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextTitle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Target: ${prog.bodyArea.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MintSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            prog.therapistNotes?.let { notes ->
                HorizontalDivider(color = SlateBorder, modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = "Notes",
                        tint = ClinicalTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBody,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseRowItem(
    exercise: Exercise,
    onPlay: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("exercise_item_${exercise.title.replace(" ", "_").lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        color = SlateCard,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ClinicalTealSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = ClinicalTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = exercise.title,
                        color = TextTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${exercise.sets} sets × ${exercise.reps} reps · ${exercise.targetMuscle}",
                        color = TextBody,
                        fontSize = 12.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SlateBorder
            )
        }
    }
}

/**
 * Rich exercise card with step-by-step description and YouTube video link.
 */
@Composable
fun ExerciseDetailCard(
    exercise: Exercise,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise_detail_${exercise.title.replace(" ", "_").lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ClinicalTealSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = ClinicalTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = exercise.title,
                            color = TextTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${exercise.sets} sets × ${exercise.reps} reps · ${exercise.targetMuscle}",
                            color = TextBody,
                            fontSize = 12.sp
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextBody
                )
            }

            // Expandable detail section
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Step-by-step instructions
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateBgElevated),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MintSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "How to Perform",
                                    color = MintSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = exercise.description,
                                color = TextBody,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Duration info (if present)
                    if (exercise.durationSeconds > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Timer, null, tint = ClinicalTeal, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Hold each rep for ${exercise.durationSeconds} seconds",
                                color = ClinicalTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // YouTube video button
                        val videoQuery = exercise.videoSearchQuery.ifBlank {
                            "${exercise.title} physical therapy exercise demonstration"
                        }
                        OutlinedButton(
                            onClick = {
                                val encodedQuery = Uri.encode(videoQuery)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CoralAlarm.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralAlarm),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.OndemandVideo, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watch Demo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Start exercise button
                        Button(
                            onClick = onPlay,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = TextOnAccent),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Exercise", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Export/Share Recovery Report card with prominent CTA button.
 */
@Composable
fun ExportReportCard(onExport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_report_card"),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, ClinicalTeal.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = ClinicalTeal,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Recovery Report",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Generate a professional PDF report with your assessment, diagnosis, exercises, and workout history to share with your clinician.",
                color = TextBody,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = TextOnAccent
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ClinicalTeal, ClinicalTealLight)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export & Share PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * AI Condition Diagnosis Card — shows what the AI thinks the injury is, confidence level, and explanation.
 */
@Composable
fun ConditionDiagnosisCard(
    conditionName: String,
    confidence: Int,
    explanation: String
) {
    val confColor = when {
        confidence >= 70 -> GreenSuccess
        confidence >= 45 -> AmberWarning
        else -> CoralAlarm
    }
    val confLabel = when {
        confidence >= 70 -> "High Confidence"
        confidence >= 45 -> "Moderate Confidence"
        else -> "Low Confidence"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, confColor.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalHospital,
                    contentDescription = null,
                    tint = confColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AI Suggested Diagnosis",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Condition name
            Text(
                text = conditionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = TextTitle
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Confidence bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Confidence", color = TextBody, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$confidence%",
                        color = confColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            confidence >= 70 -> GreenSuccessSurface
                            confidence >= 45 -> AmberWarningSurface
                            else -> CoralAlarmSurface
                        }
                    ) {
                        Text(
                            text = confLabel,
                            color = confColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SlateBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(confidence.toFloat() / 100f)
                        .background(
                            Brush.horizontalGradient(
                                listOf(confColor, confColor.copy(alpha = 0.6f))
                            )
                        )
                )
            }

            // Explanation
            if (explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = explanation,
                    color = TextBody,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚕ This is an AI suggestion, not a clinical diagnosis. Always consult your physician.",
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

/**
 * Patient Assessment Report Card — renders all stored assessment data.
 */
@Composable
fun PatientReportCard(assessment: UserAssessment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    tint = MintSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your Assessment Summary",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                    fontSize = 14.sp
                )
            }

            HorizontalDivider(color = SlateBorder, modifier = Modifier.padding(vertical = 10.dp))

            // Data rows
            AssessmentDataRow(label = "Body Area", value = assessment.bodyArea.lowercase().replaceFirstChar { it.uppercase() })
            AssessmentDataRow(label = "Pain Intensity", value = "${assessment.painIntensity}/10", valueColor = getSeverityColorForIntensity(assessment.painIntensity))
            AssessmentDataRow(label = "Triage Status", value = assessment.triageStatus.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })

            assessment.selectedMovement?.let { movement ->
                AssessmentDataRow(label = "Recorded Movement", value = movement)
            }
            assessment.targetAngle?.let { angle ->
                AssessmentDataRow(label = "Target Angle", value = "${angle}°")
            }
            assessment.scanAngleResult?.let { rom ->
                AssessmentDataRow(label = "Measured ROM", value = "${rom}°", valueColor = ClinicalTealLight)
            }

            // Symptoms description
            if (assessment.freeTextDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Symptoms", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // Strip the auto-appended motion plan text for cleaner display
                    text = assessment.freeTextDescription.substringBefore("\n\n[Clinical AI"),
                    color = TextBody,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
fun AssessmentDataRow(label: String, value: String, valueColor: Color = TextTitle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextBody, fontSize = 12.sp)
        Text(text = value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun getSeverityColorForIntensity(intensity: Int): Color {
    return when (intensity) {
        in 1..3 -> GreenSuccess
        in 4..6 -> AmberWarning
        else -> CoralAlarm
    }
}

@Composable
fun WeeklyActivityChartCard(logs: List<ExerciseProgress>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weekly Activity",
                fontWeight = FontWeight.Bold,
                color = TextTitle,
                fontSize = 15.sp
            )
            Text(
                text = "Workout frequency this week",
                color = TextBody,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val dayPadding = 20f
                val barCount = days.size
                val spaceBetweenBars = (canvasWidth - (dayPadding * 2)) / barCount

                val calendar = java.util.Calendar.getInstance()
                val counts = IntArray(barCount) { 0 }
                logs.forEach { log ->
                    calendar.timeInMillis = log.timestamp
                    val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                    val mappedIdx = when (dayOfWeek) {
                        java.util.Calendar.MONDAY -> 0
                        java.util.Calendar.TUESDAY -> 1
                        java.util.Calendar.WEDNESDAY -> 2
                        java.util.Calendar.THURSDAY -> 3
                        java.util.Calendar.FRIDAY -> 4
                        java.util.Calendar.SATURDAY -> 5
                        java.util.Calendar.SUNDAY -> 6
                        else -> 0
                    }
                    counts[mappedIdx]++
                }

                val maxCount = (counts.maxOrNull() ?: 1).coerceAtLeast(3)

                for (idx in 0 until barCount) {
                    val count = counts[idx]
                    val pct = count.toFloat() / maxCount.toFloat()
                    val barWidth = 14.dp.toPx()
                    val barHeight = canvasHeight * 0.7f * pct

                    val x = dayPadding + (idx * spaceBetweenBars) + (spaceBetweenBars / 2) - (barWidth / 2)
                    val y = canvasHeight * 0.75f - barHeight

                    // Background bar
                    drawRoundRect(
                        color = SlateBorder,
                        topLeft = Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, canvasHeight * 0.75f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Active bar
                    if (count > 0) {
                        drawRoundRect(
                            color = ClinicalTeal,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                    Text(text = d, color = TextMuted, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
                }
            }
        }
    }
}

@Composable
fun completedWorkoutRowItem(log: ExerciseProgress) {
    val dateText = remember(log.timestamp) {
        SimpleDateFormat("EEE, MMM dd (HH:mm)", Locale.getDefault()).format(Date(log.timestamp))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SlateCard,
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = "Success",
                tint = GreenSuccess,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.exerciseTitle,
                    color = TextTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${log.setsCompleted} sets × ${log.repsCompleted} reps",
                    color = TextBody,
                    fontSize = 12.sp
                )
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Timeline, contentDescription = "Pain", tint = MintSecondary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pain: ${log.painLevelPre}/10 → ${log.painLevelPost}/10",
                        color = MintSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = dateText,
                color = TextMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.End
            )
        }
    }
}

// ─── Exercise Player Dialog ───

@Composable
fun ExercisePlayerDialog(
    exercise: Exercise,
    programId: Long,
    onDismiss: () -> Unit,
    onCompleted: (sets: Int, reps: Int, prePain: Int, postPain: Int, note: String) -> Unit
) {
    var timerSecondsRemaining by remember { mutableStateOf(exercise.durationSeconds.coerceAtLeast(30)) }
    var timerRunning by remember { mutableStateOf(false) }
    var currentSetCompleteness by remember { mutableStateOf(1) }
    var prePainLog by remember { mutableStateOf(5) }
    var postPainLog by remember { mutableStateOf(4) }
    var clinicianUserNotes by remember { mutableStateOf("") }
    var stepFlowState by remember { mutableStateOf(1) }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (timerSecondsRemaining > 0) {
                delay(1000)
                timerSecondsRemaining--
            }
            timerRunning = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (stepFlowState == 1) exercise.title else "Log Outcome",
                fontWeight = FontWeight.Bold,
                color = TextTitle,
                fontSize = 18.sp
            )
        },
        containerColor = SlateCardElevated,
        confirmButton = {
            if (stepFlowState == 1) {
                Button(
                    onClick = { stepFlowState = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = TextOnAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("submit_rep_set_log_button")
                ) {
                    Text("Complete", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        onCompleted(currentSetCompleteness, exercise.reps, prePainLog, postPainLog, clinicianUserNotes)
                    },
                    modifier = Modifier.testTag("save_finished_log_action"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = TextOnAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Log", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_active_player_action")) {
                Text("Cancel", color = TextBody)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (stepFlowState == 1) {
                    Text(
                        text = exercise.description,
                        color = TextBody,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("${exercise.sets} sets") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = ClinicalTeal,
                                containerColor = ClinicalTealSurface
                            )
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("${exercise.reps} reps") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = ClinicalTeal,
                                containerColor = ClinicalTealSurface
                            )
                        )
                    }

                    HorizontalDivider(color = SlateBorder)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(text = "Exercise Timer", color = TextMuted, fontSize = 11.sp)
                        Text(
                            text = String.format("%02d:%02d", timerSecondsRemaining / 60, timerSecondsRemaining % 60),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = TextTitle,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconButton(onClick = { timerRunning = !timerRunning }) {
                                Icon(
                                    imageVector = if (timerRunning) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "Toggle",
                                    tint = ClinicalTeal,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(onClick = {
                                timerSecondsRemaining = exercise.durationSeconds.coerceAtLeast(30)
                                timerRunning = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Reset",
                                    tint = TextBody,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Rate your pain before and after to help the AI adapt your program.",
                        color = TextBody,
                        fontSize = 12.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Pain BEFORE: $prePainLog/10", color = TextTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = prePainLog.toFloat(),
                            onValueChange = { prePainLog = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(activeTrackColor = ClinicalTeal, thumbColor = ClinicalTeal, inactiveTrackColor = SlateBorder)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Pain AFTER: $postPainLog/10", color = TextTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = postPainLog.toFloat(),
                            onValueChange = { postPainLog = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(activeTrackColor = MintSecondary, thumbColor = MintSecondary, inactiveTrackColor = SlateBorder)
                        )
                    }

                    OutlinedTextField(
                        value = clinicianUserNotes,
                        onValueChange = { clinicianUserNotes = it },
                        placeholder = { Text("Add notes (optional)...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClinicalTeal,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = TextTitle,
                            unfocusedTextColor = TextTitle,
                            cursorColor = ClinicalTeal
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }
    )
}

// ─── Video AI Lab Tab ───

@Composable
fun VideoAiLabTab(viewModel: RehabViewModel) {
    val activeProgram by viewModel.activeProgram.collectAsState()
    val selectedTask by viewModel.selectedTask.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingVideo.collectAsState()
    val analysisResult by viewModel.videoAnalysisResult.collectAsState()

    val areaStr = activeProgram?.bodyArea ?: "KNEE"
    val areaDisplayName = remember(areaStr) {
        areaStr.lowercase().replaceFirstChar { it.uppercase() }
    }

    val activities = remember(areaStr) {
        when (areaStr.uppercase()) {
            "KNEE" -> listOf("Deep Knee Bend (Squat)", "Single Leg Standing Balance", "Knee Extension Wall-Slide")
            "SHOULDER" -> listOf("Wall Crawl Climb", "Shoulder Abduction (T-pose Raise)", "Pendulum Swings")
            "ANKLE", "ANKLE_FOOT" -> listOf("Ankle Pumps in Extension", "Alphabet Trace Space", "Heel Raises")
            "SPINE_BACK", "SPINE", "BACK" -> listOf("Cat-Cow Stretch Flexion", "Abdominal Pelvic Tilt", "Glute Bridge Lifting")
            "NECK" -> listOf("Neck Sideways Lateral Flexion", "Neck Rotation", "Neck Extension")
            "ELBOW" -> listOf("Forearm Pronation", "Elbow Flexion Hold", "Elbow Extension Stretch")
            "WRIST_HAND" -> listOf("Wrist Extension Stretches", "Fist Clench and Release", "Thumb-to-Finger Opposition")
            "HIP" -> listOf("Hip Abduction Side-Raise", "Standing Hip Extension", "Hip Flexion Marching")
            else -> listOf("Submaximal Joint Flexion Test", "Comfortable Range Rotation Extension")
        }
    }

    var selectedSimulatedPerformance by remember { mutableStateOf("Pain-Free / Normal Space") }
    var recordingTimerRemaining by remember { mutableStateOf(-1) }
    var showRecordingHud by remember { mutableStateOf(false) }

    LaunchedEffect(recordingTimerRemaining) {
        if (recordingTimerRemaining > 0) {
            delay(1000)
            recordingTimerRemaining--
        } else if (recordingTimerRemaining == 0) {
            recordingTimerRemaining = -1
            showRecordingHud = false
            val parsedTask = selectedTask ?: activities.firstOrNull() ?: "Joint Flexion"
            val bitmap = createSimulatedPerformanceBitmap(areaStr, parsedTask, selectedSimulatedPerformance)
            viewModel.analyzeVideoActivity(parsedTask, bitmap, selectedSimulatedPerformance)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Lab banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ClinicalTealSurface),
            border = BorderStroke(1.dp, ClinicalTeal.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ClinicalTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video Lab",
                        tint = ClinicalTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "AI Motion & Pain Lab",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextTitle
                    )
                    Text(
                        text = "Joint tracking & facial pain expression analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBody
                    )
                }
            }
        }

        if (selectedTask == null) {
            // Task list
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Activities ($areaDisplayName)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextTitle
                )

                Text(
                    text = "Select an activity to record and analyze with AI vision.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextBody,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                activities.forEachIndexed { idx, activity ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectTask(activity) }
                            .testTag("activity_task_card_$idx"),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        border = BorderStroke(1.dp, SlateBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ClinicalTealSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${idx + 1}", color = ClinicalTeal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(text = activity, color = TextTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(text = "Target: $areaDisplayName", color = TextBody, fontSize = 11.sp)
                                }
                            }
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start", tint = ClinicalTeal, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        } else {
            val task = selectedTask!!

            // Task header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.selectTask(null) }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextTitle)
                }
                Text(
                    text = task,
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                TextButton(onClick = { viewModel.selectTask(null) }) {
                    Text("Change", color = ClinicalTeal, fontSize = 12.sp)
                }
            }

            // Video viewport
            Card(
                modifier = Modifier.fillMaxWidth().height(260.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, if (showRecordingHud) CoralAlarm else SlateBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (showRecordingHud) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Videocam, "Recording", tint = CoralAlarm, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Recording... ${recordingTimerRemaining}s", color = TextTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("[AI tracking joints...]", color = TextBody, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(color = CoralAlarm, trackColor = SlateBorder, modifier = Modifier.width(180.dp))
                        }
                    } else if (isAnalyzing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ClinicalTeal, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("AI analyzing performance...", color = ClinicalTeal, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Decoding joint & facial data...", color = TextBody, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    } else if (analysisResult != null) {
                        val bitmap = remember(selectedSimulatedPerformance) {
                            createSimulatedPerformanceBitmap(areaStr, task, selectedSimulatedPerformance)
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Frame", modifier = Modifier.fillMaxSize())
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Camera, null, tint = ClinicalTeal, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI Analysis Frame", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.UploadFile, "Select", tint = TextBody, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Video Upload", style = MaterialTheme.typography.titleSmall, color = TextTitle, fontWeight = FontWeight.Bold)
                            Text("Record a 5s clip of: $task", style = MaterialTheme.typography.bodySmall, color = TextBody, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showRecordingHud = true; recordingTimerRemaining = 3 },
                                colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = TextOnAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("upload_activity_video_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record & Analyze", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (analysisResult == null && !showRecordingHud && !isAnalyzing) {
                // Performance selector
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Simulation Mode", fontWeight = FontWeight.Bold, color = TextTitle, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Text("Choose performance type to test AI analysis:", style = MaterialTheme.typography.bodySmall, color = TextBody, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 12.dp))

                        val options = listOf("Pain-Free / Normal Space", "Mild Pinch / Guarding", "Severe Pain / Sharp Lock")
                        options.forEach { opt ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedSimulatedPerformance = opt },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedSimulatedPerformance == opt,
                                    onClick = { selectedSimulatedPerformance = opt },
                                    colors = RadioButtonDefaults.colors(selectedColor = ClinicalTeal, unselectedColor = SlateBorder)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = opt, color = if (selectedSimulatedPerformance == opt) TextTitle else TextBody, fontSize = 13.sp, fontWeight = if (selectedSimulatedPerformance == opt) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            // Analysis results
            if (analysisResult != null && !showRecordingHud && !isAnalyzing) {
                val res = analysisResult!!

                // ROM card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SlateCard), border = BorderStroke(1.dp, SlateBorder), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("1. Range of Motion", fontWeight = FontWeight.Bold, color = ClinicalTeal, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Flexion Angle", style = MaterialTheme.typography.bodySmall, color = TextBody)
                                Text("${res.calculatedRomDegrees}°", fontSize = 28.sp, fontWeight = FontWeight.Black, color = TextTitle)
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = if (res.guardingDetected) AmberWarningSurface else GreenSuccessSurface, border = BorderStroke(1.dp, if (res.guardingDetected) AmberWarning else GreenSuccess)) {
                                Text(if (res.guardingDetected) "GUARDING" else "OPTIMAL", color = if (res.guardingDetected) AmberWarning else GreenSuccess, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(SlateBorder)) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((res.calculatedRomDegrees.toFloat() / 180f).coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(ClinicalTeal, ClinicalTealLight))))
                        }
                    }
                }

                // Grimace card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SlateCard), border = BorderStroke(1.dp, SlateBorder), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("2. Facial Pain Analysis", fontWeight = FontWeight.Bold, color = MintSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Grimace Index", style = MaterialTheme.typography.bodySmall, color = TextBody)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${res.maxPainGrimaceIndex}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = TextTitle)
                                    Text("/10", fontSize = 14.sp, color = TextBody, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
                                }
                            }
                            val pColor = when { res.maxPainGrimaceIndex < 3.0 -> GreenSuccess; res.maxPainGrimaceIndex < 7.0 -> AmberWarning; else -> CoralAlarm }
                            val pSurface = when { res.maxPainGrimaceIndex < 3.0 -> GreenSuccessSurface; res.maxPainGrimaceIndex < 7.0 -> AmberWarningSurface; else -> CoralAlarmSurface }
                            Surface(shape = RoundedCornerShape(6.dp), color = pSurface, border = BorderStroke(1.dp, pColor)) {
                                Text(res.painLevelClassification.uppercase(), color = pColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(value = res.maxPainGrimaceIndex.toFloat(), onValueChange = {}, valueRange = 0f..10f, enabled = false, colors = SliderDefaults.colors(
                            disabledThumbColor = when { res.maxPainGrimaceIndex < 3.0 -> GreenSuccess; res.maxPainGrimaceIndex < 7.0 -> AmberWarning; else -> CoralAlarm },
                            disabledActiveTrackColor = when { res.maxPainGrimaceIndex < 3.0 -> GreenSuccess; res.maxPainGrimaceIndex < 7.0 -> AmberWarning; else -> CoralAlarm },
                            disabledInactiveTrackColor = SlateBorder
                        ))
                    }
                }

                // Clinical feedback card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SlateCard), border = BorderStroke(1.dp, SlateBorder), shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AI Clinical Feedback", fontWeight = FontWeight.Bold, color = TextTitle, style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider(color = SlateBorder, modifier = Modifier.padding(vertical = 10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.ContactSupport, "Safety", tint = ClinicalTeal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Safety: ${res.safetyAssessment}", style = MaterialTheme.typography.bodySmall, color = TextBody, lineHeight = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Lightbulb, "Tips", tint = MintSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Advice: ${res.activityAdvice}", style = MaterialTheme.typography.bodySmall, color = TextBody, lineHeight = 16.sp)
                        }
                    }
                }

                // Save button
                var progressSaved by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        viewModel.logWorkout(programId = activeProgram?.id ?: 0L, exerciseTitle = "[Video] $task", sets = 1, reps = 1, painPre = res.maxPainGrimaceIndex.toInt().coerceIn(1, 10), painPost = res.maxPainGrimaceIndex.toInt().coerceIn(1, 10), note = "ROM: ${res.calculatedRomDegrees}°. Grimace: ${res.maxPainGrimaceIndex}/10.")
                        progressSaved = true
                    },
                    enabled = !progressSaved,
                    colors = ButtonDefaults.buttonColors(containerColor = if (progressSaved) SlateBorder else GreenSuccess, contentColor = if (progressSaved) TextMuted else TextOnAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_finished_log_action")
                ) {
                    Icon(if (progressSaved) Icons.Default.CheckCircle else Icons.Default.Save, "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (progressSaved) "Saved!" else "Save to History", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.clearVideoAnalysis() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = TextBody),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Analyze Another", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun createSimulatedPerformanceBitmap(bodyArea: String, activityName: String, performanceType: String): Bitmap {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    canvas.drawColor(SlateCard.toArgb())

    val accentColor = when (performanceType) {
        "Pain-Free / Normal Space" -> GreenSuccess.toArgb()
        "Mild Pinch / Guarding" -> AmberWarning.toArgb()
        else -> CoralAlarm.toArgb()
    }

    val gridPaint = Paint().apply { color = android.graphics.Color.argb(15, 255, 255, 255); strokeWidth = 1f }
    for (i in 0..size step 32) {
        canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), gridPaint)
        canvas.drawLine(0f, i.toFloat(), size.toFloat(), i.toFloat(), gridPaint)
    }

    val uiPaint = Paint().apply { color = TextBody.toArgb(); textSize = 14f; isAntiAlias = true }
    canvas.drawText("REC [AI SCAN]", 30f, 40f, uiPaint)
    canvas.drawText("FPS: 30 / VLM V2", 30f, size - 30f, uiPaint)

    val recPaint = Paint().apply { color = CoralAlarm.toArgb(); style = Paint.Style.FILL }
    canvas.drawCircle(size - 40f, 32f, 6f, recPaint)

    val bonePaint = Paint().apply { color = TextTitle.toArgb(); strokeWidth = 12f; strokeCap = Paint.Cap.ROUND }
    val jointIndicatorPaint = Paint().apply { color = accentColor; style = Paint.Style.STROKE; strokeWidth = 3f }
    val jointFillPaint = Paint().apply { color = accentColor; style = Paint.Style.FILL }

    when (bodyArea.uppercase()) {
        "KNEE" -> {
            val romAngle = when (performanceType) { "Pain-Free / Normal Space" -> 125.0; "Mild Pinch / Guarding" -> 95.0; else -> 65.0 }
            canvas.drawLine(150f, 150f, 250f, 260f, bonePaint)
            val angleRad = java.lang.Math.toRadians(romAngle)
            val footX = (250.0 - 120.0 * java.lang.Math.cos(angleRad)).toFloat()
            val footY = (260.0 + 120.0 * java.lang.Math.sin(angleRad)).toFloat()
            canvas.drawLine(250f, 260f, footX, footY, bonePaint)
            canvas.drawCircle(250f, 260f, 12f, jointFillPaint)
            canvas.drawCircle(250f, 260f, 30f, jointIndicatorPaint)
        }
        "SHOULDER" -> {
            val romAngle = when (performanceType) { "Pain-Free / Normal Space" -> 155.0; "Mild Pinch / Guarding" -> 110.0; else -> 75.0 }
            canvas.drawLine(250f, 320f, 250f, 220f, bonePaint)
            val angleRad = java.lang.Math.toRadians(romAngle - 90.0)
            val armX = (250.0 + 130.0 * java.lang.Math.cos(angleRad)).toFloat()
            val armY = (220.0 - 130.0 * java.lang.Math.sin(angleRad)).toFloat()
            canvas.drawLine(250f, 220f, armX, armY, bonePaint)
            canvas.drawCircle(250f, 220f, 12f, jointFillPaint)
            canvas.drawCircle(250f, 220f, 30f, jointIndicatorPaint)
        }
        else -> {
            canvas.drawLine(160f, 256f, 256f, 256f, bonePaint)
            canvas.drawLine(256f, 256f, 350f, 200f, bonePaint)
            canvas.drawCircle(256f, 256f, 12f, jointFillPaint)
        }
    }

    // Face HUD
    val faceBoxPaint = Paint().apply { color = accentColor; style = Paint.Style.STROKE; strokeWidth = 2f }
    val boxLeft = size - 150f; val boxTop = 60f; val boxRight = size - 30f; val boxBottom = 180f
    canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, faceBoxPaint)
    canvas.drawText("FACE LOG", boxLeft + 10f, boxTop + 20f, uiPaint)

    val faceCenterY = (boxTop + boxBottom) / 2 + 10f
    val faceCenterX = (boxLeft + boxRight) / 2
    val faceStrokePaint = Paint().apply { color = TextTitle.toArgb(); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }

    canvas.drawCircle(faceCenterX, faceCenterY, 30f, faceStrokePaint)

    when (performanceType) {
        "Pain-Free / Normal Space" -> {
            canvas.drawLine(faceCenterX - 10f, faceCenterY - 10f, faceCenterX - 6f, faceCenterY - 12f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 6f, faceCenterY - 12f, faceCenterX + 10f, faceCenterY - 10f, faceStrokePaint)
            val path = android.graphics.Path().apply { addArc(android.graphics.RectF(faceCenterX - 12f, faceCenterY - 5f, faceCenterX + 12f, faceCenterY + 15f), 0f, 180f) }
            canvas.drawPath(path, faceStrokePaint)
        }
        "Mild Pinch / Guarding" -> {
            canvas.drawLine(faceCenterX - 12f, faceCenterY - 10f, faceCenterX - 4f, faceCenterY - 10f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 10f, faceCenterX + 12f, faceCenterY - 10f, faceStrokePaint)
            canvas.drawLine(faceCenterX - 10f, faceCenterY + 10f, faceCenterX + 10f, faceCenterY + 10f, faceStrokePaint)
        }
        else -> {
            canvas.drawLine(faceCenterX - 12f, faceCenterY - 14f, faceCenterX - 4f, faceCenterY - 10f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 10f, faceCenterX + 12f, faceCenterY - 14f, faceStrokePaint)
            canvas.drawLine(faceCenterX - 10f, faceCenterY - 8f, faceCenterX - 4f, faceCenterY - 5f, faceStrokePaint)
            canvas.drawLine(faceCenterX - 10f, faceCenterY - 5f, faceCenterX - 4f, faceCenterY - 8f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 8f, faceCenterX + 10f, faceCenterY - 5f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 5f, faceCenterX + 10f, faceCenterY - 8f, faceStrokePaint)
            canvas.drawRect(faceCenterX - 10f, faceCenterY + 6f, faceCenterX + 10f, faceCenterY + 16f, faceStrokePaint)
        }
    }

    val overlayTextPaint = Paint().apply { color = accentColor; textSize = 14f; isAntiAlias = true; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
    val scoreText = when (performanceType) { "Pain-Free / Normal Space" -> "GRIMACE: 0.8/10"; "Mild Pinch / Guarding" -> "GRIMACE: 4.2/10"; else -> "GRIMACE: 8.6/10 !!" }
    canvas.drawText(scoreText, boxLeft + 10f, boxBottom - 10f, overlayTextPaint)

    return bitmap
}
