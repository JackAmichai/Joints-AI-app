package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.db.AppDatabase
import com.example.data.repository.RehabRepository
import com.example.ui.screens.ClinicianDashboardScreen
import com.example.ui.screens.JointIntakeScreen
import com.example.ui.screens.PatientDashboardScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RehabViewModel
import com.example.ui.viewmodel.RehabViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Boot Local Persistence Database & Repository
        val database = AppDatabase.getDatabase(this)
        val repository = RehabRepository(database.rehabDao())
        
        // Constructor injection of state ViewModels
        val viewModelFactory = RehabViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[RehabViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "welcome",
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Onboarding / Landing page
                    composable("welcome") {
                        WelcomeScreen(
                            onStartAssessment = {
                                viewModel.clearIntakeForm()
                                navController.navigate("intake")
                            },
                            onEnterClinicianPortal = {
                                navController.navigate("clinician")
                            }
                        )
                    }

                    // Stepper Joint Intake page (Body parts, pain details, ROM scanner)
                    composable("intake") {
                        JointIntakeScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onAssessmentCompleted = { _ ->
                                navController.navigate("dashboard") {
                                    popUpTo("welcome") { saveState = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    // Patient recovery logs & history dashboard
                    composable("dashboard") {
                        PatientDashboardScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.navigate("welcome") { popUpTo(0) } },
                            onEnterClinicianPortalForProgram = { _ ->
                                navController.navigate("clinician")
                            }
                        )
                    }

                    // Clinician verification queue
                    composable("clinician") {
                        ClinicianDashboardScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
