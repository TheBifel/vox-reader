package com.bifel.voxreader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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
            ).build()
    }
}
