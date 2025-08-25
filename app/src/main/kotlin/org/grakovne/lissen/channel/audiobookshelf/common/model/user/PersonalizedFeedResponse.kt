package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep

@Keep
data class PersonalizedFeedResponse(
  val id: String,
  val labelStringKey: String,
  val entities: List<PersonalizedFeedItemResponse>,
)

@Keep
data class PersonalizedFeedItemResponse(
  val id: String,
  val libraryId: String,
  val media: PersonalizedFeedItemMediaResponse,
  val updateAt: Long,
)

@Keep
data class PersonalizedFeedItemMediaResponse(
  val id: String,
  val metadata: PersonalizedFeedItemMetadataResponse,
)

@Keep
data class PersonalizedFeedItemMetadataResponse(
  val title: String,
  val subtitle: String?,
  val authorName: String,
)
