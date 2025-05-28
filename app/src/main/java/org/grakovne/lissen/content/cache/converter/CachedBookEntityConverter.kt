package org.grakovne.lissen.content.cache.converter

import com.google.gson.reflect.TypeToken
import org.grakovne.lissen.content.cache.converter.CachedBookEntityDetailedConverter.Companion.gson
import org.grakovne.lissen.content.cache.entity.BookEntity
import org.grakovne.lissen.content.cache.entity.BookSeriesDto
import org.grakovne.lissen.domain.Book
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
              val type = object : TypeToken<List<BookSeriesDto>>() {}.type
              gson.fromJson<List<BookSeriesDto>>(it, type)
            }?.joinToString(", ") { series ->
              buildString {
                append(series.title)
                series.sequence
                  ?.takeIf(String::isNotBlank)
                  ?.let { append(" #$it") }
              }
            },
        duration = entity.duration,
        hasContent = true,
      )
  }
