package com.orbit.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.orbit.core.data.db.migration.DatabaseMigrations
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrbitDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2_preservesAllEntitiesAndAppliesDefaultValues() {
        // Step 1: Create version 1 database and insert sample data across all 7 entities
        var db = helper.createDatabase(TEST_DB, 1)

        // 1. tasks
        db.execSQL("""
            INSERT INTO tasks (id, title, notes, due_date_epoch_day, due_time_minute_of_day, priority, is_completed, completed_at_epoch_ms, reminder_at_epoch_ms, created_at_epoch_ms, updated_at_epoch_ms)
            VALUES ('task-1', 'Initial Task', 'Notes here', 20000, 600, 'HIGH', 0, NULL, NULL, 1000, 1000)
        """.trimIndent())

        // 2. calendar_events
        db.execSQL("""
            INSERT INTO calendar_events (id, calendar_id, external_event_id, title, description, start_epoch_ms, end_epoch_ms, is_all_day, location, attendees_count, last_synced_at_epoch_ms)
            VALUES ('cal-1', 1, 101, 'Sprint Planning', 'Planning meeting', 2000, 3000, 0, 'Room A', 5, 1000)
        """.trimIndent())

        // 3. meeting_notes
        db.execSQL("""
            INSERT INTO meeting_notes (id, event_snapshot_id, title, content, created_at_epoch_ms, updated_at_epoch_ms)
            VALUES ('note-1', 'cal-1', 'Prep Notes', 'Discuss sprint velocity', 1000, 1000)
        """.trimIndent())

        // 4. video_items
        db.execSQL("""
            INSERT INTO video_items (id, url, title, tags_json, notes, priority, estimated_duration_minutes, watch_intent, is_archived, created_at_epoch_ms, updated_at_epoch_ms)
            VALUES ('vid-1', 'https://example.com/video', 'Tech Talk', '["kotlin"]', 'Watch for architecture', 'MEDIUM', 45, 'LEARNING', 0, 1000, 1000)
        """.trimIndent())

        // 5. reel_counter_sessions
        db.execSQL("""
            INSERT INTO reel_counter_sessions (id, local_date, started_at_epoch_ms, paused_at_epoch_ms, stopped_at_epoch_ms, status, created_at_epoch_ms, updated_at_epoch_ms)
            VALUES ('sess-1', '2026-10-20', 1000, NULL, 5000, 'STOPPED', 1000, 5000)
        """.trimIndent())

        // 6. counter_events
        db.execSQL("""
            INSERT INTO counter_events (id, session_id, timestamp_epoch_ms, action, sequence_number)
            VALUES ('evt-1', 'sess-1', 1050, 'INCREMENT', 1)
        """.trimIndent())

        // 7. daily_reel_statistics
        db.execSQL("""
            INSERT INTO daily_reel_statistics (local_date, manual_count, active_duration_ms, instagram_foreground_duration_ms, confidence)
            VALUES ('2026-10-20', 1, 4000, 6000, 1.0)
        """.trimIndent())

        db.close()

        // Step 2: Migrate to version 2 and validate schema automatically against 2.json
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, DatabaseMigrations.MIGRATION_1_2)

        // Step 3: Verify all 7 entities survived migration intact
        val taskCursor = db.query("SELECT id, title, is_archived FROM tasks WHERE id = 'task-1'")
        assertThat(taskCursor.moveToFirst()).isTrue()
        assertThat(taskCursor.getString(taskCursor.getColumnIndexOrThrow("title"))).isEqualTo("Initial Task")
        assertThat(taskCursor.getInt(taskCursor.getColumnIndexOrThrow("is_archived"))).isEqualTo(0)
        taskCursor.close()

        val calCursor = db.query("SELECT id, title FROM calendar_events WHERE id = 'cal-1'")
        assertThat(calCursor.moveToFirst()).isTrue()
        assertThat(calCursor.getString(calCursor.getColumnIndexOrThrow("title"))).isEqualTo("Sprint Planning")
        calCursor.close()

        val noteCursor = db.query("SELECT id, title, content FROM meeting_notes WHERE id = 'note-1'")
        assertThat(noteCursor.moveToFirst()).isTrue()
        assertThat(noteCursor.getString(noteCursor.getColumnIndexOrThrow("content"))).isEqualTo("Discuss sprint velocity")
        noteCursor.close()

        val vidCursor = db.query("SELECT id, title FROM video_items WHERE id = 'vid-1'")
        assertThat(vidCursor.moveToFirst()).isTrue()
        assertThat(vidCursor.getString(vidCursor.getColumnIndexOrThrow("title"))).isEqualTo("Tech Talk")
        vidCursor.close()

        val sessCursor = db.query("SELECT id, status FROM reel_counter_sessions WHERE id = 'sess-1'")
        assertThat(sessCursor.moveToFirst()).isTrue()
        assertThat(sessCursor.getString(sessCursor.getColumnIndexOrThrow("status"))).isEqualTo("STOPPED")
        sessCursor.close()

        val evtCursor = db.query("SELECT id, action FROM counter_events WHERE id = 'evt-1'")
        assertThat(evtCursor.moveToFirst()).isTrue()
        assertThat(evtCursor.getString(evtCursor.getColumnIndexOrThrow("action"))).isEqualTo("INCREMENT")
        evtCursor.close()

        val statCursor = db.query("SELECT local_date, manual_count FROM daily_reel_statistics WHERE local_date = '2026-10-20'")
        assertThat(statCursor.moveToFirst()).isTrue()
        assertThat(statCursor.getInt(statCursor.getColumnIndexOrThrow("manual_count"))).isEqualTo(1)
        statCursor.close()

        db.close()
    }
}
