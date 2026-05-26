package com.example.data.repository

import com.example.data.db.RehabDao
import com.example.data.model.UserAssessment
import com.example.data.model.ExerciseProgram
import com.example.data.model.ExerciseProgress
import kotlinx.coroutines.flow.Flow

class RehabRepository(private val rehabDao: RehabDao) {
    
    val allAssessments: Flow<List<UserAssessment>> = rehabDao.getAllAssessments()
    val allPrograms: Flow<List<ExerciseProgram>> = rehabDao.getAllPrograms()
    val allProgressLogs: Flow<List<ExerciseProgress>> = rehabDao.getAllProgressLogs()

    suspend fun getAssessmentById(id: Long): UserAssessment? {
        return rehabDao.getAssessmentById(id)
    }

    suspend fun insertAssessment(assessment: UserAssessment): Long {
        return rehabDao.insertAssessment(assessment)
    }

    suspend fun deleteAssessment(assessment: UserAssessment) {
        rehabDao.deleteAssessment(assessment)
    }

    fun getProgramById(id: Long): Flow<ExerciseProgram?> {
        return rehabDao.getProgramById(id)
    }

    fun getProgramsByStatus(status: String): Flow<List<ExerciseProgram>> {
        return rehabDao.getProgramsByStatus(status)
    }

    fun getProgramForAssessment(assessmentId: Long): Flow<ExerciseProgram?> {
        return rehabDao.getProgramForAssessment(assessmentId)
    }

    suspend fun insertProgram(program: ExerciseProgram): Long {
        return rehabDao.insertProgram(program)
    }

    suspend fun updateProgram(program: ExerciseProgram) {
        rehabDao.updateProgram(program)
    }

    fun getProgressForProgram(programId: Long): Flow<List<ExerciseProgress>> {
        return rehabDao.getProgressForProgram(programId)
    }

    suspend fun insertProgress(progress: ExerciseProgress): Long {
        return rehabDao.insertProgress(progress)
    }
}
