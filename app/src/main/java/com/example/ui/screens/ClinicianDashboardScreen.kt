package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Exercise
import com.example.data.model.ExerciseProgram
import com.example.ui.theme.*
import com.example.ui.viewmodel.RehabViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicianDashboardScreen(
    viewModel: RehabViewModel,
    onNavigateBack: () -> Unit
) {
    val programs by viewModel.programs.collectAsState()
    val assessments by viewModel.assessments.collectAsState()

    val pendingPrograms = remember(programs) {
        programs.filter { it.clinicianStatus == "PENDING" }
    }

    var selectedProgram by remember { mutableStateOf<ExerciseProgram?>(null) }
    var visibleReviewModal by remember { mutableStateOf(false) }

    // Inside dynamic review variables
    var therapistName by remember { mutableStateOf("Sarah Adams, DPT") }
    var therapistNotes by remember { mutableStateOf("") }
    val editableExercises = remember { mutableStateListOf<Exercise>() }

    LaunchedEffect(selectedProgram) {
        selectedProgram?.let { prog ->
            therapistNotes = "Vetted for general knee/hip stability. Increase reps as soreness subsides."
            editableExercises.clear()
            editableExercises.addAll(prog.exercises)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clinical Review Console", color = TextTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextTitle)
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
                // Header clinical banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = ClinicalTeal.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, ClinicalTeal)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = "Clinical Secure", tint = ClinicalTeal, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Professional Supervision Desk", color = TextTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Review, optimize sets/reps of AI-drafted routines, and approve logs for patient practice.", color = TextBody, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }

                Text(
                    text = "Pending Plan Sign-offs (${pendingPrograms.size})",
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (pendingPrograms.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = "Cleared",
                            tint = SlateBorder,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Review Queue Cleared",
                            color = TextTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All AI-generated files signed-off. No pending assessments currently require clinician interaction.",
                            color = TextBody,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(pendingPrograms) { _, prog ->
                            // Match relevant assessment details to reveal ROM statistics
                            val matchAssess = assessments.find { it.id == prog.assessmentId }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProgram = prog
                                        visibleReviewModal = true
                                    }
                                    .testTag("pending_program_review_${prog.id}"),
                                shape = RoundedCornerShape(10.dp),
                                color = SlateCard,
                                border = BorderStroke(1.dp, SlateBorder)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = prog.title,
                                            fontWeight = FontWeight.Bold,
                                            color = TextTitle,
                                            fontSize = 15.sp
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = ClinicalTeal.copy(alpha = 0.08f)
                                        ) {
                                            Text(
                                                text = prog.bodyArea.replace("_", " "),
                                                color = ClinicalTeal,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    matchAssess?.let { ass ->
                                        Text(
                                            text = "Patient Report: \"${ass.freeTextDescription}\"",
                                            color = TextBody,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )

                                        if (ass.scanAngleResult != null) {
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Timeline,
                                                    contentDescription = "ROM Angle",
                                                    tint = MintSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Vision Scan ROM: ${ass.scanAngleResult}° verified angle restrictions.",
                                                    color = MintSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = "Click to Review & Sign-off",
                                            color = ClinicalTeal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Clinical review editor dialog sheet
    if (visibleReviewModal && selectedProgram != null) {
        val prog = selectedProgram!!
        val matchingAssessment = assessments.find { it.id == prog.assessmentId }

        AlertDialog(
            onDismissRequest = { visibleReviewModal = false },
            title = {
                Text(
                    text = "Vetting Treatment Plan",
                    fontWeight = FontWeight.Black,
                    color = TextTitle,
                    fontSize = 18.sp
                )
            },
            containerColor = SlateCard,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveProgram(
                            programId = prog.id,
                            therapistName = therapistName,
                            therapistNotes = therapistNotes,
                            reviewedExercises = editableExercises.toList()
                        )
                        visibleReviewModal = false
                        selectedProgram = null
                    },
                    modifier = Modifier.testTag("clinician_approve_decision_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Approve & Activate Program", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.rejectProgram(prog.id, "Rejected as inappropriate by clinician reviewer.")
                        visibleReviewModal = false
                        selectedProgram = null
                    },
                    modifier = Modifier.testTag("clinician_reject_decision_button")
                ) {
                    Text("Reject Plan", color = CoralAlarm)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Patient Intake summary
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateBg),
                            border = BorderStroke(1.dp, SlateBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Patient Pain Background", color = MintSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Description: \"${matchingAssessment?.freeTextDescription ?: "Symptoms unknown"}\"", color = TextBody, fontSize = 11.sp, lineHeight = 15.sp)
                                matchingAssessment?.scanAngleResult?.let { angle ->
                                    Text("Calculated flexion flexion angle: $angle° (VLM Measured)", color = ClinicalTeal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Clinician Identity
                    item {
                        OutlinedTextField(
                            value = therapistName,
                            onValueChange = { therapistName = it },
                            label = { Text("Physiotherapist Name") },
                            modifier = Modifier.fillMaxWidth().testTag("clinician_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ClinicalTeal,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = TextTitle,
                                unfocusedTextColor = TextTitle
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Exercises modifier queue
                    item {
                        Text("Review AI-Drafted Drills", color = TextTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("You can modify sets and repetitions of each exercise below:", color = TextBody, fontSize = 11.sp)
                    }

                    itemsIndexed(editableExercises) { idx, exercise ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateBg),
                            border = BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exercise.title, fontWeight = FontWeight.Bold, color = TextTitle, fontSize = 12.sp)
                                    Text(exercise.targetMuscle, color = MintSecondary, fontSize = 10.sp)
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Sets modifier text field
                                    OutlinedTextField(
                                        value = exercise.sets.toString(),
                                        onValueChange = { input ->
                                            val num = input.toIntOrNull() ?: 0
                                            editableExercises[idx] = exercise.copy(sets = num)
                                        },
                                        modifier = Modifier.width(52.dp).testTag("exercise_set_input_$idx"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextTitle, unfocusedTextColor = TextTitle)
                                    )
                                    Text("sets", fontSize = 11.sp, color = TextBody)

                                    // Reps modifier text field
                                    OutlinedTextField(
                                        value = exercise.reps.toString(),
                                        onValueChange = { input ->
                                            val num = input.toIntOrNull() ?: 0
                                            editableExercises[idx] = exercise.copy(reps = num)
                                        },
                                        modifier = Modifier.width(52.dp).testTag("exercise_rep_input_$idx"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextTitle, unfocusedTextColor = TextTitle)
                                    )
                                    Text("reps", fontSize = 11.sp, color = TextBody)
                                }
                            }
                        }
                    }

                    // Feedback comments
                    item {
                        OutlinedTextField(
                            value = therapistNotes,
                            onValueChange = { therapistNotes = it },
                            label = { Text("Clinical Feedback & Exercises Instructions") },
                            modifier = Modifier.fillMaxWidth().height(100.dp).testTag("clinician_notes_input"),
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
}
