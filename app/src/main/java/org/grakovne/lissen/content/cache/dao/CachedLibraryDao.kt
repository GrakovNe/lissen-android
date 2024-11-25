package org.grakovne.lissen.content.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.grakovne.lissen.content.cache.entity.CachedLibraryEntity
import org.grakovne.lissen.domain.Library

@Dao
interface CachedLibraryDao {

    @Transaction
    suspend fun updateLibraries(libraries: List<Library>) {
        val entities = libraries.map {
            CachedLibraryEntity(
                id = it.id,
                title = it.title,
                type = it.type,
            )
        }

        upsertLibraries(entities)
        deleteLibrariesExcept(entities.map { it.id })
    }

    @Transaction
    @Query("SELECT * FROM libraries WHERE id = :libraryId")
    suspend fun fetchLibrary(libraryId: String): CachedLibraryEntity?

    /**
     * Due to we are not allows to download podcast for now,
     * it's strictly required to hide podcast libraries from the list
     */
    @Transaction
    @Query("SELECT * FROM libraries WHERE type = 'LIBRARY'")
    suspend fun fetchLibraries(): List<CachedLibraryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraries(libraries: List<CachedLibraryEntity>)

    @Query("DELETE FROM libraries WHERE id NOT IN (:ids)")
    suspend fun deleteLibrariesExcept(ids: List<String>)
}
