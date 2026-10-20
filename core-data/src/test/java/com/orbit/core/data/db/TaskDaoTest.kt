package com.orbit.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orbit.core.data.db.dao.TaskDao
import com.orbit.core.data.db.entity.TaskEntity
import com.orbit.core.domain.model.Priority
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class TaskDaoTest {

    private lateinit var db: OrbitDatabase
    private lateinit var taskDao: TaskDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = db.taskDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertTask_andQueryById() = runTest {
        val task = TaskEntity(
            id = "task-1",
            title = "Buy groceries",
            notes = "Apples, Milk, Bread",
            dueDateEpochDay = 20000,
            priority = Priority.HIGH,
            isCompleted = false,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )

        taskDao.upsertTask(task)
        val fetched = taskDao.getTaskById("task-1").first()

        assertThat(fetched).isNotNull()
        assertThat(fetched?.title).isEqualTo("Buy groceries")
        assertThat(fetched?.priority).isEqualTo(Priority.HIGH)
    }

    @Test
    fun updateCompletionStatus_togglesCorrectly() = runTest {
        val task = TaskEntity(
            id = "task-2",
            title = "Read Orbit docs",
            isCompleted = false,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        taskDao.upsertTask(task)

        taskDao.updateCompletionStatus("task-2", isCompleted = true, completedAt = 2000L, updatedAt = 2000L)
        val updated = taskDao.getTaskByIdDirect("task-2")

        assertThat(updated?.isCompleted).isTrue()
        assertThat(updated?.completedAtEpochMs).isEqualTo(2000L)
    }

    @Test
    fun getTasksForDate_filtersCorrectly() = runTest {
        val task1 = TaskEntity(
            id = "task-a",
            title = "Today Task",
            dueDateEpochDay = 19500,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        val task2 = TaskEntity(
            id = "task-b",
            title = "Tomorrow Task",
            dueDateEpochDay = 19501,
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )

        taskDao.upsertTasks(listOf(task1, task2))

        val tasksForDay = taskDao.getTasksForDate(19500).first()
        assertThat(tasksForDay).hasSize(1)
        assertThat(tasksForDay.first().id).isEqualTo("task-a")
    }

    @Test
    fun deleteTaskById_andDeleteAll() = runTest {
        val task = TaskEntity(
            id = "task-del",
            title = "To be deleted",
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 1000L
        )
        taskDao.upsertTask(task)
        taskDao.deleteTaskById("task-del")

        assertThat(taskDao.getTaskByIdDirect("task-del")).isNull()
    }
}
