package org.grakovne.lissen.content.cache.persistent.converter

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.content.cache.persistent.entity.BookEntity
import org.grakovne.lissen.content.cache.persistent.entity.BookSeriesDto
import org.grakovne.lissen.lib.domain.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityConverter
  @Inject
  constructor() {
    fun apply(entity: BookEntity): Book =
      Book(
        id = entity.id,
        title = entity.title,
        subtitle = entity.subtitle,
        author = entity.author,
        series =
          entity
            .seriesJson
            ?.let {
              val type = Types.newParameterizedType(List::class.java, BookSeriesDto::class.java)
              val adapter = moshi.adapter<List<BookSeriesDto>>(type)
              adapter.fromJson(it)
            }?.joinToString(", ") { series ->
              buildString {
                append(series.title)
                series.sequence
                  ?.takeIf(String::isNotBlank)
                  ?.let { append(" #$it") }
              }
            },
      )
  }
