package com.example.data.repository

import com.example.data.database.VideoProjectDao
import com.example.data.database.VideoProjectEntity
import kotlinx.coroutines.flow.Flow

class VideoProjectRepository(private val projectDao: VideoProjectDao) {

    val allProjects: Flow<List<VideoProjectEntity>> = projectDao.observeAllProjects()

    suspend fun saveOrUpdateProject(project: VideoProjectEntity): Long {
        return projectDao.insertProject(project)
    }

    suspend fun renameProject(id: Long, newName: String) {
        projectDao.renameProject(id, newName)
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteProjectById(id)
    }

    suspend fun clearAllProjects() {
        projectDao.clearAllProjects()
    }
}
