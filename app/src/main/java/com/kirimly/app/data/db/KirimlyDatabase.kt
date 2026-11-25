package com.kirimly.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransferRecordEntity::class, UploadSessionEntity::class, ApprovedTokenEntity::class],
    version = 2,
    exportSchema = false
)
abstract class KirimlyDatabase : RoomDatabase() {

    abstract fun transferDao(): TransferDao

    abstract fun uploadSessionDao(): UploadSessionDao

    companion object {
        private const val DATABASE_NAME = "kirimly.db"

        /** v1 → v2 adds resumable-upload tables; transfer history is kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `upload_sessions` (" +
                        "`id` TEXT NOT NULL, `token` TEXT NOT NULL, `file_index` INTEGER NOT NULL, " +
                        "`file_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size` INTEGER NOT NULL, " +
                        "`relative_dir` TEXT NOT NULL, `peer_name` TEXT NOT NULL, `expected_sha256` TEXT, " +
                        "`key_b64` TEXT, `target_uri` TEXT NOT NULL, `legacy_path` TEXT, " +
                        "`initial_name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `approved_tokens` (" +
                        "`token` TEXT NOT NULL, `request_json` TEXT NOT NULL, `expires_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`token`))"
                )
            }
        }

        @Volatile
        private var instance: KirimlyDatabase? = null

        fun getInstance(context: Context): KirimlyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KirimlyDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
