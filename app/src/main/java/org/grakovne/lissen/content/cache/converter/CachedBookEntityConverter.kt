package org.grakovne.lissen.content.cache.converter

import org.grakovne.lissen.content.cache.entity.BookEntity
import org.grakovne.lissen.domain.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityConverter @Inject constructor() {

    fun apply(entity: BookEntity): Book = Book(
        id = entity.id,
        title = entity.title,
        subtitle = entity.subtitle,
        author = entity.author,
        series = null, // CHANGE ME
        duration = entity.duration,
    )
}
