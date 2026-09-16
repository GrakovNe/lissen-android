package org.grakovne.lissen.persistence.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.grakovne.lissen.common.EpisodeOrdering
import org.grakovne.lissen.common.EpisodeSortKey
import org.grakovne.lissen.content.cache.persistent.dao.ItemPreferenceDao
import org.grakovne.lissen.content.cache.persistent.entity.ItemPreferenceEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemPreferencesRepository
  @Inject
  constructor(
    private val itemPreferenceDao: ItemPreferenceDao,
  ) {
    fun observeOrdering(itemId: String): Flow<EpisodeOrdering> =
      itemPreferenceDao
        .observe(itemId)
        .map { preference -> preference.toOrdering() }

    suspend fun setOrdering(
      itemId: String,
      ordering: EpisodeOrdering,
    ) {
      itemPreferenceDao.upsert(
        ItemPreferenceEntity(
          itemId = itemId,
          sortKey = ordering.key.name,
          sortAscending = ordering.ascending,
        ),
      )
    }

    private fun ItemPreferenceEntity?.toOrdering(): EpisodeOrdering {
      val preference = this ?: return EpisodeOrdering.DEFAULT

      val key =
        runCatching { EpisodeSortKey.valueOf(preference.sortKey) }
          .getOrDefault(EpisodeOrdering.DEFAULT.key)

      return EpisodeOrdering(key = key, ascending = preference.sortAscending)
    }
  }
