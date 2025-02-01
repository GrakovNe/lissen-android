package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.user.PersonalizedFeedResponse
import org.grakovne.lissen.domain.RecentBook
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentListeningResponseConverter @Inject constructor() {

    fun apply(
        response: List<PersonalizedFeedResponse>,
        progress: Map<String, Pair<Long, Double>>,
    ): List<RecentBook> = response
        .find { it.labelStringKey == LABEL_CONTINUE_LISTENING }
        ?.entities
        ?.distinctBy { it.id }
        ?.map {
            RecentBook(
                id = it.id,
                title = it.media.metadata.title,
                author = it.media.metadata.authorName,
                listenedPercentage = progress[it.id]?.second?.let { it * 100 }?.toInt(),
                lastUpdate = progress[it.id]?.first ?: 0,
            )
        } ?: emptyList()

    companion object {

        private const val LABEL_CONTINUE_LISTENING = "LabelContinueListening"
    }
}
