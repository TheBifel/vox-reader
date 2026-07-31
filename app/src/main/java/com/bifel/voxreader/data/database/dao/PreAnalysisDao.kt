package com.bifel.voxreader.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.bifel.voxreader.data.database.entity.AnalysisRunEntity
import com.bifel.voxreader.data.database.entity.CharacterEntity
import com.bifel.voxreader.data.database.entity.PhraseAttributionEntity
import com.bifel.voxreader.data.database.entity.PhraseManifestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisRunDao {
    @Upsert
    suspend fun upsert(run: AnalysisRunEntity)

    @Query("SELECT * FROM analysis_run WHERE runId = :runId")
    suspend fun find(runId: String): AnalysisRunEntity?

    @Query(
        """
        SELECT * FROM analysis_run
        WHERE bookId = :bookId AND active = 1
        LIMIT 1
        """,
    )
    fun observeActive(bookId: String): Flow<AnalysisRunEntity?>

    @Query("UPDATE analysis_run SET active = 0, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun deactivateBook(bookId: String, updatedAt: Long)

    @Query(
        """
        UPDATE analysis_run
        SET active = 1, updatedAt = :updatedAt
        WHERE runId = :runId
        """,
    )
    suspend fun activate(runId: String, updatedAt: Long)

}

@Dao
interface PhraseManifestDao {
    @Query("DELETE FROM phrase_manifest WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Upsert
    suspend fun upsert(items: List<PhraseManifestEntity>)

    @Query("SELECT * FROM phrase_manifest WHERE bookId = :bookId ORDER BY phraseIndex")
    suspend fun forBook(bookId: String): List<PhraseManifestEntity>
}

@Dao
interface PhraseAttributionDao {
    @Upsert
    suspend fun upsert(items: List<PhraseAttributionEntity>)

    @Query(
        """
        SELECT attribution.* FROM phrase_attribution AS attribution
        INNER JOIN analysis_run AS run ON run.runId = attribution.runId
        WHERE attribution.bookId = :bookId
          AND run.active = 1
        """,
    )
    suspend fun activeForBook(bookId: String): List<PhraseAttributionEntity>
}

@Dao
interface CharacterDao {
    @Upsert
    suspend fun upsert(characters: List<CharacterEntity>)

    @Query(
        """
        SELECT character.* FROM character_registry AS character
        INNER JOIN analysis_run AS run ON run.runId = character.runId
        WHERE character.bookId = :bookId AND run.active = 1
        ORDER BY character.displayName
        """,
    )
    fun observeActive(bookId: String): Flow<List<CharacterEntity>>
}
