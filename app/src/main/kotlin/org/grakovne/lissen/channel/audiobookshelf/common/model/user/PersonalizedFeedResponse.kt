package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class PersonalizedFeedResponse(
  val id: String,
  val labelStringKey: String,
  val entities: List<PersonalizedFeedItemResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class PersonalizedFeedItemResponse(
  val id: String,
  val libraryId: String,
  val media: PersonalizedFeedItemMediaResponse?,
)

@Keep
@JsonClass(generateAdapter = true)
data class PersonalizedFeedItemMediaResponse(
  val id: String,
  val metadata: PersonalizedFeedItemMetadataResponse,
)

@Keep
@JsonClass(generateAdapter = true)
data class PersonalizedFeedItemMetadataResponse(
  val title: String,
  val subtitle: String?,
  val authorName: String?,
)
