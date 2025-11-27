package com.example.neurotecsdklibrary.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized


@Database(
    entities = [FaceTemplateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao

    companion object {
        private var instance: AppDatabase? = null

        @OptIn(InternalCoroutinesApi::class)
        fun getInstance(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "face_db"
            ).build().also { instance = it}
        }


    }
}