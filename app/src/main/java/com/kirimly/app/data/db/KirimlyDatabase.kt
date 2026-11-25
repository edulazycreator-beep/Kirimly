package com.kirimly.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransferRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KirimlyDatabase : RoomDatabase() {

    abstract fun transferDao(): TransferDao

    companion object {
        private const val DATABASE_NAME = "kirimly.db"

        @Volatile
        private var instance: KirimlyDatabase? = null

        fun getInstance(context: Context): KirimlyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KirimlyDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
