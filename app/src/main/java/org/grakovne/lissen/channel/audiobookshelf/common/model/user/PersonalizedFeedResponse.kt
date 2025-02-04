package org.grakovne.lissen.channel.audiobookshelf.common.model.user

data class PersonalizedFeedResponse(
    val id: String,
    val labelStringKey: String,
    val entities: List<PersonalizedFeedItemResponse>,
)

data class PersonalizedFeedItemResponse(
    val id: String,
    val libraryId: String,
    val media: PersonalizedFeedItemMediaResponse,
    val updateAt: Long,
)

data class PersonalizedFeedItemMediaResponse(
    val id: String,
    val metadata: PersonalizedFeedItemMetadataResponse,
)

data class PersonalizedFeedItemMetadataResponse(
    val title: String,
    val subtitle: String?,
    val authorName: String,
)
