package com.example.neurotecsdklibrary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(face: FaceTemplateEntity): Long

    @Query("SELECT * FROM face_templates ORDER BY timestamp DESC")
    fun getAll(): List<FaceTemplateEntity>

    @Query("SELECT * FROM face_templates ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): FaceTemplateEntity?

    @Query("DELETE FROM face_templates WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM face_templates")
    fun deleteAll()
}
