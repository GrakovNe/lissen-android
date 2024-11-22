package org.grakovne.lissen.content.cache.converter

import org.grakovne.lissen.content.cache.entity.BookEntity
import org.grakovne.lissen.domain.RecentBook
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedBookEntityRecentConverter @Inject constructor() {

    fun apply(entity: BookEntity): RecentBook = RecentBook(
        id = entity.id,
        title = entity.title,
        author = entity.author,
    )
}
