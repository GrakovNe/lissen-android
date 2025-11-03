package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import org.grakovne.lissen.lib.domain.LibraryType
import java.io.Serializable

@Keep
@Entity(
  tableName = "libraries",
)
@JsonClass(generateAdapter = true)
data class CachedLibraryEntity(
  @PrimaryKey
  val id: String,
  val title: String,
  val type: LibraryType,
) : Serializable
