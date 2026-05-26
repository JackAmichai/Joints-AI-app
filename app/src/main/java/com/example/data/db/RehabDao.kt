package com.example.data.db

import androidx.room.*
import com.example.data.model.UserAssessment
import com.example.data.model.ExerciseProgram
import com.example.data.model.ExerciseProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface RehabDao {
    // --- User Assessment Queries ---
    @Query("SELECT * FROM user_assessments ORDER BY timestamp DESC")
    fun getAllAssessments(): Flow<List<UserAssessment>>

    @Query("SELECT * FROM user_assessments WHERE id = :id LIMIT 1")
    suspend fun getAssessmentById(id: Long): UserAssessment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: UserAssessment): Long

    @Delete
    suspend fun deleteAssessment(assessment: UserAssessment)

    // --- Exercise Program Queries ---
    @Query("SELECT * FROM exercise_programs ORDER BY timestamp DESC")
    fun getAllPrograms(): Flow<List<ExerciseProgram>>

    @Query("SELECT * FROM exercise_programs WHERE id = :id LIMIT 1")
    fun getProgramById(id: Long): Flow<ExerciseProgram?>

    @Query("SELECT * FROM exercise_programs WHERE clinicianStatus = :status ORDER BY timestamp DESC")
    fun getProgramsByStatus(status: String): Flow<List<ExerciseProgram>>

    @Query("SELECT * FROM exercise_programs WHERE assessmentId = :assessmentId LIMIT 1")
    fun getProgramForAssessment(assessmentId: Long): Flow<ExerciseProgram?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgram(program: ExerciseProgram): Long

    @Update
    suspend fun updateProgram(program: ExerciseProgram)

    // --- Exercise Progress Queries ---
    @Query("SELECT * FROM exercise_progress ORDER BY timestamp DESC")
    fun getAllProgressLogs(): Flow<List<ExerciseProgress>>

    @Query("SELECT * FROM exercise_progress WHERE programId = :programId ORDER BY timestamp DESC")
    fun getProgressForProgram(programId: Long): Flow<List<ExerciseProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ExerciseProgress): Long
}
