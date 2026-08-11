package com.business.gym.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.business.gym.data.local.entity.DailyNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyNoteDao {
    @Query("SELECT * FROM daily_notes WHERE uid = :uid AND date = :date LIMIT 1")
    fun getNote(uid: String, date: String): Flow<DailyNoteEntity?>

    @Query("SELECT * FROM daily_notes WHERE uid = :uid")
    fun getAllNotes(uid: String): Flow<List<DailyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: DailyNoteEntity)

    @Query("DELETE FROM daily_notes WHERE uid = :uid AND date = :date")
    suspend fun deleteNote(uid: String, date: String)
}
