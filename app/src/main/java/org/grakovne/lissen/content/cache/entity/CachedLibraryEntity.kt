package org.grakovne.lissen.content.cache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.grakovne.lissen.channel.common.LibraryType
import java.io.Serializable

@Entity(
    tableName = "libraries"
)
data class CachedLibraryEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: LibraryType
) : Serializable
