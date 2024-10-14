package org.grakovne.lissen.channel.audiobookshelf.model

data class PersonalizedFeedResponse(
    val id: String,
    val labelStringKey: String,
    val entities: List<PersonalizedFeedItemResponse>
)

data class PersonalizedFeedItemResponse(
    val id: String,
    val libraryId: String,
    val media: PersonalizedFeedItemMediaResponse,
)

data class PersonalizedFeedItemMediaResponse(
    val id: String,
    val metadata: PersonalizedFeedItemMetadataResponse,
)

data class PersonalizedFeedItemMetadataResponse(
    val title: String,
    val authorName: String
)