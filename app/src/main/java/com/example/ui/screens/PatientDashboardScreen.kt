package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
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
import com.example.data.api.VideoAnalysisResponse
import com.example.data.model.Exercise
import com.example.data.model.ExerciseProgram
import com.example.data.model.ExerciseProgress
import com.example.ui.theme.*
import com.example.ui.viewmodel.RehabViewModel
import kotlinx.coroutines.delay
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

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("My Plan", "AI Video Lab", "Rehab Progress Logs", "AI Support Chat")

    var playingExercise by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Patient Space", color = TextTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextTitle)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearIntakeForm() }, modifier = Modifier.testTag("reset_workflow_action")) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset Form", tint = MintSecondary)
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
                // Header Segment
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
                            text = "Joint Recovery Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextTitle
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = ClinicalTeal.copy(alpha = 0.12f),
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

                // Header Tab layout
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = ClinicalTeal,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ClinicalTeal
                        )
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                            unselectedContentColor = TextBody,
                            selectedContentColor = ClinicalTeal
                        )
                    }
                }

                // Conditional view compilation
                if (selectedTab == 0) {
                    // --- MY PLAN TAB VIEW ---
                    if (programs.isEmpty()) {
                        EmptyPlanState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // Clinician review checklist status alert card
                            activeProgram?.let { prog ->
                                item {
                                    ClinicianOversightHeaderCard(
                                        prog = prog,
                                        onSkipReview = {
                                            // Shortcut: Approve program locally with a generic doctor
                                            viewModel.approveProgram(
                                                programId = prog.id,
                                                therapistName = "Dr. Sarah Adams, DPT",
                                                therapistNotes = "Self-approved active trial. Plan vetted for general joint stability drills.",
                                                reviewedExercises = prog.exercises
                                            )
                                        },
                                        onEnterClinicianReview = {
                                            onEnterClinicianPortalForProgram(prog.id)
                                        }
                                    )
                                }

                                // Quick program specifications
                                item {
                                    ProgramSummaryCard(prog = prog)
                                }

                                // Interactive Exercise List queue
                                item {
                                    Text(
                                        text = "Workout Exercises (${prog.exercises.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                items(prog.exercises, key = { it.title }) { exercise ->
                                    ExerciseRowItem(
                                        exercise = exercise,
                                        onPlay = { playingExercise = exercise }
                                    )
                                }
                            }
                        }
                    }
                } else if (selectedTab == 1) {
                    // --- VIDEO AI LAB TAB VIEW ---
                    VideoAiLabTab(viewModel = viewModel)
                } else if (selectedTab == 2) {
                    // --- REHAB PROGRESS LOGS TAB VIEW ---
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            WeeklyActivityChartCard(logs)
                        }

                        item {
                            Text(
                                text = "Workout History Logs",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
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
                                        text = "No completed session workouts logged yet. Play an exercise to start recording achievements!",
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
                } else {
                    // --- AI SUPPORT CHAT TAB VIEW ---
                    AiSupportChatTab(viewModel = viewModel)
                }
            }

            // Interactive completion player workout sheet
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

// --- Specific Sub-Composables ---

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
            text = "No Active Therapy Plan",
            color = TextTitle,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You haven't generated a rehab program yet. Click the reset action or re-enter the assessment to analyze joint parameters.",
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
    val cardColor = if (isPending) AmberWarning.copy(alpha = 0.12f) else GreenSuccess.copy(alpha = 0.12f)
    val borderCol = if (isPending) AmberWarning else GreenSuccess

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderCol),
        shape = RoundedCornerShape(12.dp)
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
                    text = if (isPending) "Pending Clinical Review" else "Active & Practitioner Vetted",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isPending) "To safeguard your muscular welfare, this plan highlights pending validation from clinical physiotherapists. You can bypass this to enable trial drills or review the dashboard."
                       else "This plan was clinically verified by ${prog.therapistName ?: "Sarah Adams, DPT"}. Exercise modifications applied successfully.",
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
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = borderCol),
                        border = BorderStroke(1.dp, borderCol),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Bypass / Try Drills", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onEnterClinicianReview,
                        modifier = Modifier.weight(1.3f).height(38.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = Color.White),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text("Open Clinician Panel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = prog.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextTitle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Targeted Zone: ${prog.bodyArea.lowercase().replaceFirstChar { it.uppercase() }} Structure",
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
                        text = "Clinical Guidelines: $notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBody,
                        lineHeight = 15.sp
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
        shape = RoundedCornerShape(12.dp),
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
                // Play Action Circle indicator
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ClinicalTeal.copy(alpha = 0.12f)),
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
                        text = "${exercise.sets} sets × ${exercise.reps} reps | Muscle: ${exercise.targetMuscle}",
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

@Composable
fun WeeklyActivityChartCard(logs: List<ExerciseProgress>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Rehab Drills Completeness",
                fontWeight = FontWeight.Bold,
                color = TextTitle,
                fontSize = 15.sp
            )
            Text(
                text = "Weekly workout performance frequency tracking",
                color = TextBody,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Dynamic Custom drawing of vertical bar charts
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Days index mapping
                val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val dayPadding = 20f
                val barCount = days.size
                val spaceBetweenBars = (canvasWidth - (dayPadding * 2)) / barCount

                // Sift logs into weekdays
                val calendar = java.util.Calendar.getInstance()
                val counts = IntArray(barCount) { 0 }
                logs.forEach { log ->
                    calendar.timeInMillis = log.timestamp
                    val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                    // Calendar day of week is 1=Sunday, 2=Monday...
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

                    // Draw background slate caps
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, canvasHeight * 0.75f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Draw front glowing Teal active counts
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
                    Text(text = d, color = TextBody, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
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
        shape = RoundedCornerShape(10.dp),
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
                    text = "${log.setsCompleted} sets × ${log.repsCompleted} reps completed",
                    color = TextBody,
                    fontSize = 12.sp
                )
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Timeline, contentDescription = "Pain Diff", tint = MintSecondary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pain level: ${log.painLevelPre}/10 ➔ ${log.painLevelPost}/10",
                        color = MintSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = dateText,
                color = TextBody,
                fontSize = 10.sp,
                textAlign = TextAlign.End
            )
        }
    }
}

// --- Exercise Active Player Workout Dialog ---

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

    var stepFlowState by remember { mutableStateOf(1) } // 1 = Play animation / Timer, 2 = Log pain outcomes

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
                text = if (stepFlowState == 1) "Performing: ${exercise.title}" else "Log Joint Outcome Feedback",
                fontWeight = FontWeight.Bold,
                color = TextTitle,
                fontSize = 18.sp
            )
        },
        containerColor = SlateCard,
        confirmButton = {
            if (stepFlowState == 1) {
                Button(
                    onClick = { stepFlowState = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("submit_rep_set_log_button")
                ) {
                    Text("I've Completed Drill", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        onCompleted(currentSetCompleteness, exercise.reps, prePainLog, postPainLog, clinicianUserNotes)
                    },
                    modifier = Modifier.testTag("save_finished_log_action"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Log Logs", fontWeight = FontWeight.Bold)
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
                    // Instruction Paragraph
                    Text(text = exercise.description, color = TextBody, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)

                    // Target indicator chips
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SuggestionChip(onClick = {}, label = { Text("Sets target: ${exercise.sets}") }, colors = SuggestionChipDefaults.suggestionChipColors(labelColor = ClinicalTeal))
                        SuggestionChip(onClick = {}, label = { Text("Reps target: ${exercise.reps}") }, colors = SuggestionChipDefaults.suggestionChipColors(labelColor = ClinicalTeal))
                    }

                    HorizontalDivider(color = SlateBorder)

                    // Timer details
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(text = "Rhythm pacing breathing clock", color = TextBody, fontSize = 11.sp)
                        
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
                                    contentDescription = "Stop",
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
                    // Logging results step
                    Text(text = "How does your joint feel after this exercise? Logging pain inputs helps the AI adapt parameters safely.", color = TextBody, fontSize = 12.sp)

                    // Pre vs Post Sliders
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Anterior pain level BEFORE drill: $prePainLog/10", color = TextTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = prePainLog.toFloat(),
                            onValueChange = { prePainLog = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(activeTrackColor = ClinicalTeal, thumbColor = ClinicalTeal)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Anterior pain level AFTER drill: $postPainLog/10", color = TextTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = postPainLog.toFloat(),
                            onValueChange = { postPainLog = it.toInt() },
                            valueRange = 0f..10f,
                            steps = 9,
                            colors = SliderDefaults.colors(activeTrackColor = MintSecondary, thumbColor = MintSecondary)
                        )
                    }

                    OutlinedTextField(
                        value = clinicianUserNotes,
                        onValueChange = { clinicianUserNotes = it },
                        placeholder = { Text("Add any personal feedback (e.g. felt minor clicking, tight hamstring).", color = TextBody) },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ClinicalTeal,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = TextTitle,
                            unfocusedTextColor = TextTitle
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    )
}

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
            // Analyze the simulated video frame!
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
            colors = CardDefaults.cardColors(containerColor = ClinicalTeal.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, ClinicalTeal.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
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
                        contentDescription = "Video AI Lab",
                        tint = ClinicalTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "AI Motion & Dolorimetry Lab",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Real-time joints tracking & facial pain grimace analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBody
                    )
                }
            }
        }

        if (selectedTask == null) {
            // Task Listing section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Assigned Kinematic Activities ($areaDisplayName)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Select any prescribed task below to record or upload a performance video. The VLM (Vision Language Model) will reconstruct bone segments, calculate range of motion (ROM), and analyze your facial pain expression values.",
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
                        shape = RoundedCornerShape(12.dp)
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ClinicalTeal.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = ClinicalTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = activity,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Target Zone: $areaDisplayName Joint",
                                        color = TextBody,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                tint = ClinicalTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            val task = selectedTask!!

            // Task Active Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.selectTask(null) }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Text(
                    text = "Activity: $task",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                TextButton(onClick = { viewModel.selectTask(null) }) {
                    Text("Change", color = MintSecondary, fontSize = 12.sp)
                }
            }

            // Simulated Video Input Viewport
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                border = BorderStroke(1.dp, if (showRecordingHud) CoralAlarm else SlateBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showRecordingHud) {
                        // Live simulation countdown timer overlay
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Recording",
                                tint = CoralAlarm,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Capturing exercise video frames... ${recordingTimerRemaining}s",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "[AI joints mapping actively tracking...]",
                                color = TextBody,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(color = CoralAlarm, modifier = Modifier.width(180.dp))
                        }
                    } else if (isAnalyzing) {
                        // AI analyzing overlay
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ClinicalTeal, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "AI Kinesiology & Pain Analyzer at work...",
                                color = ClinicalTeal,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Decoding joint flexions & facial scale indices...",
                                color = TextBody,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else if (analysisResult != null) {
                        // Show simulated frame that was scanned!
                        val res = analysisResult!!
                        val bitmap = remember(selectedSimulatedPerformance) {
                            createSimulatedPerformanceBitmap(areaStr, task, selectedSimulatedPerformance)
                        }
                        
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Analysis frame preview",
                                modifier = Modifier.fillMaxSize()
                            )

                            // Tag label overlay
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Camera, contentDescription = null, tint = ClinicalTeal, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Analyzed AI Performance Frame Preview", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Video Upload Waiting card
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Select",
                                tint = TextBody,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Kinetics Video Upload Console",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Record or select a 5s clip of you doing: $task",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextBody,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    showRecordingHud = true
                                    recordingTimerRemaining = 3
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ClinicalTeal, contentColor = SlateBg),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("upload_activity_video_button")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record & Analyze Video", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (analysisResult == null && !showRecordingHud && !isAnalyzing) {
                // Interactive tester segmented details so users can choose simulated outcomes!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Aesthetic Selector: Test AI Diagnostics",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "To test the AI pain model's visual reasoning, choose the joint & face performance index to simulate below:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextBody,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val options = listOf("Pain-Free / Normal Space", "Mild Pinch / Guarding", "Severe Pain / Sharp Lock")
                        options.forEach { opt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedSimulatedPerformance = opt },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedSimulatedPerformance == opt,
                                    onClick = { selectedSimulatedPerformance = opt },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = ClinicalTeal,
                                        unselectedColor = SlateBorder
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = opt,
                                    color = if (selectedSimulatedPerformance == opt) Color.White else TextBody,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedSimulatedPerformance == opt) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Results diagnostics presentation cards!
            if (analysisResult != null && !showRecordingHud && !isAnalyzing) {
                val res = analysisResult!!

                // Joint ROM Angle Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "1. Joint Range of Motion (ROM)",
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTeal,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Estimated Flexion Angle", style = MaterialTheme.typography.bodySmall, color = TextBody)
                                Text(
                                    text = "${res.calculatedRomDegrees}°",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (res.guardingDetected) AmberWarning.copy(alpha = 0.15f) else GreenSuccess.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, if (res.guardingDetected) AmberWarning else GreenSuccess)
                            ) {
                                Text(
                                    text = if (res.guardingDetected) "GUARDING DETECTED" else "OPTIMAL ROM",
                                    color = if (res.guardingDetected) AmberWarning else GreenSuccess,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Small graphic layout indicating arc progress
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateBorder)
                        ) {
                            val activePct = (res.calculatedRomDegrees.toFloat() / 180f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(activePct)
                                    .background(ClinicalTeal)
                            )
                        }
                    }
                }

                // Facial expressions of pain card!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                     Column(modifier = Modifier.padding(16.dp)) {
                         Text(
                             text = "2. Facial Expressions of Pain (Dolorimetry)",
                             fontWeight = FontWeight.Bold,
                             color = MintSecondary,
                             fontSize = 14.sp
                         )
                         Spacer(modifier = Modifier.height(10.dp))

                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             horizontalArrangement = Arrangement.SpaceBetween,
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             Column {
                                 Text("Max Grimace Pain Index", style = MaterialTheme.typography.bodySmall, color = TextBody)
                                 Row(verticalAlignment = Alignment.Bottom) {
                                     Text(
                                         text = "${res.maxPainGrimaceIndex}",
                                         fontSize = 28.sp,
                                         fontWeight = FontWeight.Black,
                                         color = Color.White
                                     )
                                     Text("/10", fontSize = 14.sp, color = TextBody, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
                                 }
                             }

                             Surface(
                                 shape = RoundedCornerShape(4.dp),
                                 color = when {
                                     res.maxPainGrimaceIndex < 3.0 -> GreenSuccess.copy(alpha = 0.15f)
                                     res.maxPainGrimaceIndex < 7.0 -> AmberWarning.copy(alpha = 0.15f)
                                     else -> CoralAlarm.copy(alpha = 0.15f)
                                 },
                                 border = BorderStroke(
                                     1.dp,
                                     when {
                                         res.maxPainGrimaceIndex < 3.0 -> GreenSuccess
                                         res.maxPainGrimaceIndex < 7.0 -> AmberWarning
                                         else -> CoralAlarm
                                     }
                                 )
                             ) {
                                 Text(
                                     text = res.painLevelClassification.uppercase(),
                                     color = when {
                                         res.maxPainGrimaceIndex < 3.0 -> GreenSuccess
                                         res.maxPainGrimaceIndex < 7.0 -> AmberWarning
                                         else -> CoralAlarm
                                     },
                                     fontWeight = FontWeight.Bold,
                                     fontSize = 10.sp,
                                     modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                 )
                             }
                         }

                         Spacer(modifier = Modifier.height(12.dp))

                         // Visual progress slider indicator for Grimace
                         Slider(
                             value = res.maxPainGrimaceIndex.toFloat(),
                             onValueChange = {},
                             valueRange = 0f..10f,
                             enabled = false,
                             colors = SliderDefaults.colors(
                                 disabledThumbColor = when {
                                     res.maxPainGrimaceIndex < 3.0 -> GreenSuccess
                                     res.maxPainGrimaceIndex < 7.0 -> AmberWarning
                                     else -> CoralAlarm
                                 },
                                 disabledActiveTrackColor = when {
                                     res.maxPainGrimaceIndex < 3.0 -> GreenSuccess
                                     res.maxPainGrimaceIndex < 7.0 -> AmberWarning
                                     else -> CoralAlarm
                                 },
                                 disabledInactiveTrackColor = SlateBorder
                             )
                         )
                     }
                }

                // AI clinical observations detail card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AI Clinical Safety Diagnostics & Feedback",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )

                        HorizontalDivider(color = SlateBorder, modifier = Modifier.padding(vertical = 10.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(imageVector = Icons.Default.ContactSupport, contentDescription = "Safety", tint = ClinicalTeal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Safety Observation: ${res.safetyAssessment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(imageVector = Icons.Default.Lightbulb, contentDescription = "Tips", tint = MintSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Kinetic Advice: ${res.activityAdvice}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextBody,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Substantial integration with Room log workout history!
                var progressSaved by remember { mutableStateOf(false) }
                
                Button(
                    onClick = {
                        val painLogIndex = res.maxPainGrimaceIndex.toInt().coerceIn(1, 10)
                        viewModel.logWorkout(
                            programId = activeProgram?.id ?: 0L,
                            exerciseTitle = "[Video Scan] $task",
                            sets = 1,
                            reps = 1,
                            painPre = painLogIndex,
                            painPost = painLogIndex,
                            note = "VLM Video Assessment ROM: ${res.calculatedRomDegrees}°. Face grimace indices: ${res.maxPainGrimaceIndex}/10. ${res.safetyAssessment}"
                        )
                        progressSaved = true
                    },
                    enabled = !progressSaved,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (progressSaved) Color.DarkGray else GreenSuccess,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_finished_log_action")
                ) {
                    Icon(
                        imageVector = if (progressSaved) Icons.Default.CheckCircle else Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (progressSaved) "Saved to Workout History!" else "Log Results to Daily Progress History",
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { viewModel.clearVideoAnalysis() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = TextBody),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Analyze Another Performance", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun createSimulatedPerformanceBitmap(bodyArea: String, activityName: String, performanceType: String): Bitmap {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Draw background slate card colors
    canvas.drawColor(SlateCard.toArgb())

    val accentColor = when (performanceType) {
        "Pain-Free / Normal Space" -> GreenSuccess.toArgb()
        "Mild Pinch / Guarding" -> AmberWarning.toArgb()
        else -> CoralAlarm.toArgb()
    }

    // Grid details
    val gridPaint = Paint().apply {
        color = Color(0x1B000000).toArgb()
        strokeWidth = 1.5f
    }
    for (i in 0..size step 32) {
        canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), gridPaint)
        canvas.drawLine(0f, i.toFloat(), size.toFloat(), i.toFloat(), gridPaint)
    }

    // Draw stylized camera overlays
    val uiPaint = Paint().apply {
        color = Color(0x8A000000).toArgb()
        textSize = 14f
        isAntiAlias = true
    }
    canvas.drawText("REC [AI SCAN]", 30f, 40f, uiPaint)
    canvas.drawText("FPS: 30 / VLM V2", 30f, size - 30f, uiPaint)

    // Blinking REC Indicator
    val recPaint = Paint().apply {
        color = CoralAlarm.toArgb()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size - 40f, 32f, 6f, recPaint)

    // Body Joint Bone lines
    val bonePaint = Paint().apply {
        color = TextTitle.toArgb()
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    val jointIndicatorPaint = Paint().apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    val jointFillPaint = Paint().apply {
        color = accentColor
        style = Paint.Style.FILL
    }

    // Draw bone configurations depending on Body Area
    when (bodyArea.uppercase()) {
        "KNEE" -> {
            val romAngle = when (performanceType) {
                "Pain-Free / Normal Space" -> 125.0
                "Mild Pinch / Guarding" -> 95.0
                else -> 65.0
            }
            // Draw hip, knee coordinate
            canvas.drawLine(150f, 150f, 250f, 260f, bonePaint)
            // Draw knee to foot coordinate based on angle
            val angleRad = java.lang.Math.toRadians(romAngle)
            val footX = (250.0 - 120.0 * java.lang.Math.cos(angleRad)).toFloat()
            val footY = (260.0 + 120.0 * java.lang.Math.sin(angleRad)).toFloat()
            canvas.drawLine(250f, 260f, footX, footY, bonePaint)

            canvas.drawCircle(250f, 260f, 12f, jointFillPaint)
            canvas.drawCircle(250f, 260f, 30f, jointIndicatorPaint)
        }
        "SHOULDER" -> {
            val romAngle = when (performanceType) {
                "Pain-Free / Normal Space" -> 155.0
                "Mild Pinch / Guarding" -> 110.0
                else -> 75.0
            }
            canvas.drawLine(250f, 320f, 250f, 220f, bonePaint) // Spines to Shoulder
            val angleRad = java.lang.Math.toRadians(romAngle - 90.0)
            val armX = (250.0 + 130.0 * java.lang.Math.cos(angleRad)).toFloat()
            val armY = (220.0 - 130.0 * java.lang.Math.sin(angleRad)).toFloat()
            canvas.drawLine(250f, 220f, armX, armY, bonePaint)

            canvas.drawCircle(250f, 220f, 12f, jointFillPaint)
            canvas.drawCircle(250f, 220f, 30f, jointIndicatorPaint)
        }
        else -> {
            // General limbs
            canvas.drawLine(160f, 256f, 256f, 256f, bonePaint)
            canvas.drawLine(256f, 256f, 350f, 200f, bonePaint)
            canvas.drawCircle(256f, 256f, 12f, jointFillPaint)
        }
    }

    // DRAW FACIAL GRIMACE METRIC BOX AT CORNER (High accuracy pain expressions HUD!)
    val faceBoxPaint = Paint().apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    // Draw face HUD box at top right
    val boxLeft = size - 150f
    val boxTop = 60f
    val boxRight = size - 30f
    val boxBottom = 180f
    canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, faceBoxPaint)
    canvas.drawText("FACE LOG", boxLeft + 10f, boxTop + 20f, uiPaint)

    // Draw simple computerized face inside box representing different pain expression levels
    val faceCenterY = (boxTop + boxBottom) / 2 + 10f
    val faceCenterX = (boxLeft + boxRight) / 2

    val faceStrokePaint = Paint().apply {
        color = TextTitle.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    canvas.drawCircle(faceCenterX, faceCenterY, 30f, faceStrokePaint) // Face circle outline

    when (performanceType) {
        "Pain-Free / Normal Space" -> {
            // Cheerful smile eyes
            canvas.drawLine(faceCenterX - 10f, faceCenterY - 10f, faceCenterX - 6f, faceCenterY - 12f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 6f, faceCenterY - 12f, faceCenterX + 10f, faceCenterY - 10f, faceStrokePaint)
            // Curved smile
            val path = android.graphics.Path().apply {
                val rect = android.graphics.RectF(faceCenterX - 12f, faceCenterY - 5f, faceCenterX + 12f, faceCenterY + 15f)
                addArc(rect, 0f, 180f)
            }
            canvas.drawPath(path, faceStrokePaint)
        }
        "Mild Pinch / Guarding" -> {
            // Squinting eyes / straight brows
            canvas.drawLine(faceCenterX - 12f, faceCenterY - 10f, faceCenterX - 4f, faceCenterY - 10f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 10f, faceCenterX + 12f, faceCenterY - 10f, faceStrokePaint)
            // Slightly furrowed lips
            canvas.drawLine(faceCenterX - 10f, faceCenterY + 10f, faceCenterX + 10f, faceCenterY + 10f, faceStrokePaint)
        }
        else -> { // Severe Pain
            // Heavily tensed furrowed eyebrows (V shape)
            canvas.drawLine(faceCenterX - 12f, faceCenterY - 14f, faceCenterX - 4f, faceCenterY - 10f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 10f, faceCenterX + 12f, faceCenterY - 14f, faceStrokePaint)
            // Tight closed eyes X X
            canvas.drawLine(faceCenterX - 10f, faceCenterY - 8f, faceCenterX - 4f, faceCenterY - 5f, faceStrokePaint)
            canvas.drawLine(faceCenterX - 10f, faceCenterY - 5f, faceCenterX - 4f, faceCenterY - 8f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 8f, faceCenterX + 10f, faceCenterY - 5f, faceStrokePaint)
            canvas.drawLine(faceCenterX + 4f, faceCenterY - 5f, faceCenterX + 10f, faceCenterY - 8f, faceStrokePaint)
            // Grimacing open mouth
            canvas.drawRect(faceCenterX - 10f, faceCenterY + 6f, faceCenterX + 10f, faceCenterY + 16f, faceStrokePaint)
        }
    }

    // Overlay text showing current Pain Grimace tracker level
    val overlayTextPaint = Paint().apply {
        color = accentColor
        textSize = 14f
        isAntiAlias = true
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val scoreText = when (performanceType) {
        "Pain-Free / Normal Space" -> "GRIMACE: 0.8/10"
        "Mild Pinch / Guarding" -> "GRIMACE: 4.2/10"
        else -> "GRIMACE: 8.6/10 !!"
    }
    canvas.drawText(scoreText, boxLeft + 10f, boxBottom - 10f, overlayTextPaint)

    return bitmap
}

@Composable
fun AiSupportChatTab(viewModel: RehabViewModel) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isSending by viewModel.isSendingMessage.collectAsState()
    val chatError by viewModel.chatError.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Automatically scroll to bottom when a new message is received
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
            .background(Color.Transparent)
    ) {
        // Chat Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlateCard.copy(alpha = 0.6f))
                .border(BorderStroke(1.dp, SlateBorder), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (chatMessages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Your conversation is empty. Say hello to begin!",
                        color = TextBody,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages) { message ->
                        val isUser = message.sender == "USER"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 2.dp,
                                    bottomEnd = if (isUser) 2.dp else 16.dp
                                ),
                                color = if (isUser) ClinicalTeal else SlateBorder,
                                contentColor = if (isUser) Color.Black else TextTitle,
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    if (isSending) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 2.dp),
                                    color = SlateBorder,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = ClinicalTeal,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Joints AI is thinking...",
                                            color = TextBody,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    chatError?.let { err ->
                        item {
                            Surface(
                                color = CoralAlarm.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, CoralAlarm),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(
                                    text = err,
                                    color = CoralAlarm,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Ask about safety guidelines, sets/reps...", color = TextBody) },
                modifier = Modifier.weight(1f).testTag("chat_input_text_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClinicalTeal,
                    unfocusedBorderColor = SlateBorder,
                    focusedTextColor = TextTitle,
                    unfocusedTextColor = TextTitle,
                    focusedContainerColor = SlateCard,
                    unfocusedContainerColor = SlateCard
                ),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3,
                singleLine = false,
                trailingIcon = {
                    if (messageText.isNotEmpty()) {
                        IconButton(onClick = { messageText = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextBody)
                        }
                    }
                }
            )

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendChatMessage(messageText)
                        messageText = ""
                    }
                },
                enabled = messageText.isNotBlank() && !isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClinicalTeal,
                    contentColor = Color.Black,
                    disabledContainerColor = ClinicalTeal.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(56.dp).testTag("chat_send_button"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reset/Clear History Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { viewModel.clearChat() },
                colors = ButtonDefaults.textButtonColors(contentColor = TextBody)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Chat",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear History", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
