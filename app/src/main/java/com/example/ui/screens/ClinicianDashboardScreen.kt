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

    var therapistName by remember { mutableStateOf("Sarah Adams, DPT") }
    var therapistNotes by remember { mutableStateOf("") }
    val editableExercises = remember { mutableStateListOf<Exercise>() }

    LaunchedEffect(selectedProgram) {
        selectedProgram?.let { prog ->
            therapistNotes = "Vetted for general joint stability. Increase reps as soreness subsides."
            editableExercises.clear()
            editableExercises.addAll(prog.exercises)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clinician Console", color = TextTitle, fontWeight = FontWeight.Bold) },
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
                // Header banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = ClinicalTealSurface),
                    border = BorderStroke(1.dp, ClinicalTeal.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Clinical Secure",
                            tint = ClinicalTeal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Professional Supervision",
                                color = TextTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Review and approve AI-drafted rehabilitation programs.",
                                color = TextBody,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Text(
                    text = "Pending Reviews (${pendingPrograms.size})",
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
                            text = "Queue Cleared",
                            color = TextTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All AI-generated plans have been reviewed. No pending assessments require attention.",
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
                            val matchAssess = assessments.find { it.id == prog.assessmentId }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProgram = prog
                                        visibleReviewModal = true
                                    }
                                    .testTag("pending_program_review_${prog.id}"),
                                shape = RoundedCornerShape(14.dp),
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
                                            shape = RoundedCornerShape(6.dp),
                                            color = ClinicalTealSurface
                                        ) {
                                            Text(
                                                text = prog.bodyArea.replace("_", " "),
                                                color = ClinicalTeal,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    matchAssess?.let { ass ->
                                        Text(
                                            text = "\"${ass.freeTextDescription}\"",
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
                                                    text = "ROM: ${ass.scanAngleResult}° measured",
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
                                            text = "Tap to Review →",
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

    // Review dialog
    if (visibleReviewModal && selectedProgram != null) {
        val prog = selectedProgram!!
        val matchingAssessment = assessments.find { it.id == prog.assessmentId }

        AlertDialog(
            onDismissRequest = { visibleReviewModal = false },
            title = {
                Text(
                    text = "Review Treatment Plan",
                    fontWeight = FontWeight.Black,
                    color = TextTitle,
                    fontSize = 18.sp
                )
            },
            containerColor = SlateCardElevated,
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
                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = TextOnAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Approve & Activate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.rejectProgram(prog.id, "Rejected by clinician reviewer.")
                        visibleReviewModal = false
                        selectedProgram = null
                    },
                    modifier = Modifier.testTag("clinician_reject_decision_button")
                ) {
                    Text("Reject", color = CoralAlarm)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Patient summary
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateBgElevated),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Patient Background", color = MintSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "\"${matchingAssessment?.freeTextDescription?.substringBefore("\n\n[Clinical AI") ?: "No description"}\"",
                                    color = TextBody,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                matchingAssessment?.scanAngleResult?.let { angle ->
                                    Text(
                                        "Measured flexion: $angle°",
                                        color = ClinicalTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // AI Condition Diagnosis
                    if (prog.conditionName != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateBgElevated),
                                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalHospital, null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Suspected Condition", color = AmberWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${prog.conditionName} (${prog.conditionConfidence ?: 0}% confidence)",
                                        color = TextTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    prog.conditionExplanation?.let { expl ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(expl, color = TextBody, fontSize = 11.sp, lineHeight = 15.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Therapist name
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
                                unfocusedTextColor = TextTitle,
                                cursorColor = ClinicalTeal,
                                focusedLabelColor = ClinicalTeal,
                                unfocusedLabelColor = TextBody
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Exercise editor
                    item {
                        Text("Review Exercises", color = TextTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Modify sets and reps as needed:", color = TextBody, fontSize = 11.sp)
                    }

                    itemsIndexed(editableExercises) { idx, exercise ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateBgElevated),
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
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
                                    OutlinedTextField(
                                        value = exercise.sets.toString(),
                                        onValueChange = { input ->
                                            val num = input.toIntOrNull() ?: 0
                                            editableExercises[idx] = exercise.copy(sets = num)
                                        },
                                        modifier = Modifier.width(52.dp).testTag("exercise_set_input_$idx"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextTitle,
                                            unfocusedTextColor = TextTitle,
                                            focusedBorderColor = ClinicalTeal,
                                            unfocusedBorderColor = SlateBorder,
                                            cursorColor = ClinicalTeal
                                        )
                                    )
                                    Text("sets", fontSize = 11.sp, color = TextBody)

                                    OutlinedTextField(
                                        value = exercise.reps.toString(),
                                        onValueChange = { input ->
                                            val num = input.toIntOrNull() ?: 0
                                            editableExercises[idx] = exercise.copy(reps = num)
                                        },
                                        modifier = Modifier.width(52.dp).testTag("exercise_rep_input_$idx"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextTitle,
                                            unfocusedTextColor = TextTitle,
                                            focusedBorderColor = ClinicalTeal,
                                            unfocusedBorderColor = SlateBorder,
                                            cursorColor = ClinicalTeal
                                        )
                                    )
                                    Text("reps", fontSize = 11.sp, color = TextBody)
                                }
                            }
                        }
                    }

                    // Feedback notes
                    item {
                        OutlinedTextField(
                            value = therapistNotes,
                            onValueChange = { therapistNotes = it },
                            label = { Text("Clinical Feedback & Notes") },
                            modifier = Modifier.fillMaxWidth().height(100.dp).testTag("clinician_notes_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ClinicalTeal,
                                unfocusedBorderColor = SlateBorder,
                                focusedTextColor = TextTitle,
                                unfocusedTextColor = TextTitle,
                                cursorColor = ClinicalTeal,
                                focusedLabelColor = ClinicalTeal,
                                unfocusedLabelColor = TextBody
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        )
    }
}
