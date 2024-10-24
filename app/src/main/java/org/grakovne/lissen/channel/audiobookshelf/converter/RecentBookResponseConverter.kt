package org.grakovne.lissen.channel.audiobookshelf.converter

import org.grakovne.lissen.channel.audiobookshelf.model.PersonalizedFeedResponse
import org.grakovne.lissen.domain.RecentBook
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentBookResponseConverter @Inject constructor() {

    fun apply(response: List<PersonalizedFeedResponse>): List<RecentBook> = response
        .find { it.labelStringKey == LABEL_CONTINUE_LISTENING }
        ?.entities
        ?.map {
            RecentBook(
                id = it.id,
                title = it.media.metadata.title,
                author = it.media.metadata.authorName
            )
        } ?: emptyList()

    companion object {
        private const val LABEL_CONTINUE_LISTENING = "LabelContinueListening"
    }
}
