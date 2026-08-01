package com.bifel.voxreader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bifel.voxreader.data.database.dao.AnalysisRunDao
import com.bifel.voxreader.data.database.dao.CharacterDao
import com.bifel.voxreader.data.database.dao.PhraseAttributionDao
import com.bifel.voxreader.data.database.dao.PhraseManifestDao
import com.bifel.voxreader.data.database.entity.AnalysisRunEntity
import com.bifel.voxreader.data.database.entity.CharacterEntity
import com.bifel.voxreader.data.database.entity.PhraseAttributionEntity
import com.bifel.voxreader.data.database.entity.PhraseManifestEntity

@Database(
    entities = [
        AnalysisRunEntity::class,
        PhraseManifestEntity::class,
        PhraseAttributionEntity::class,
        CharacterEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class VoxReaderDatabase : RoomDatabase() {
    abstract fun analysisRunDao(): AnalysisRunDao
    abstract fun phraseManifestDao(): PhraseManifestDao
    abstract fun phraseAttributionDao(): PhraseAttributionDao
    abstract fun characterDao(): CharacterDao

    companion object {
        fun create(context: Context): VoxReaderDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                VoxReaderDatabase::class.java,
                "voxreader-attribution.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE phrase_attribution ADD COLUMN emotionType TEXT")
                db.execSQL("ALTER TABLE phrase_attribution ADD COLUMN emotionIntensity REAL")
                db.execSQL("ALTER TABLE phrase_attribution ADD COLUMN emotionConfidence REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE character_registry ADD COLUMN ageBand TEXT")
                db.execSQL("ALTER TABLE character_registry ADD COLUMN genderPresentation TEXT")
                db.execSQL("ALTER TABLE character_registry ADD COLUMN vocalRegister TEXT")
            }
        }
    }
}
