package org.grakovne.lissen.persistence.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.common.EpisodeOrdering
import org.grakovne.lissen.common.EpisodeSortKey
import org.grakovne.lissen.content.cache.persistent.dao.ItemPreferenceDao
import org.grakovne.lissen.content.cache.persistent.entity.ItemPreferenceEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemPreferencesRepositoryTest {
  private class FakeItemPreferenceDao : ItemPreferenceDao {
    private val rows = MutableStateFlow<Map<String, ItemPreferenceEntity>>(emptyMap())

    override fun observe(itemId: String): Flow<ItemPreferenceEntity?> = rows.map { it[itemId] }

    override suspend fun upsert(preference: ItemPreferenceEntity) {
      rows.value = rows.value + (preference.itemId to preference)
    }
  }

  private val dao = FakeItemPreferenceDao()
  private val repository = ItemPreferencesRepository(dao)

  @Test
  fun `ordering falls back to default when nothing is stored`() =
    runBlocking {
      assertEquals(EpisodeOrdering.DEFAULT, repository.observeOrdering("podcast-1").first())
    }

  @Test
  fun `ordering round-trips through the repository`() =
    runBlocking {
      val orderings =
        listOf(
          EpisodeOrdering(EpisodeSortKey.PUBLISHED_AT, ascending = false),
          EpisodeOrdering(EpisodeSortKey.TITLE, ascending = true),
          EpisodeOrdering(EpisodeSortKey.TITLE, ascending = false),
          EpisodeOrdering(EpisodeSortKey.SEASON, ascending = true),
          EpisodeOrdering(EpisodeSortKey.EPISODE, ascending = false),
          EpisodeOrdering(EpisodeSortKey.FILENAME, ascending = false),
        )

      orderings.forEach { ordering ->
        repository.setOrdering("podcast-1", ordering)
        assertEquals(ordering, repository.observeOrdering("podcast-1").first())
      }
    }

  @Test
  fun `ordering is stored per item`() =
    runBlocking {
      repository.setOrdering("podcast-1", EpisodeOrdering(EpisodeSortKey.TITLE, ascending = false))
      repository.setOrdering("podcast-2", EpisodeOrdering(EpisodeSortKey.FILENAME, ascending = true))

      assertEquals(
        EpisodeOrdering(EpisodeSortKey.TITLE, ascending = false),
        repository.observeOrdering("podcast-1").first(),
      )
      assertEquals(
        EpisodeOrdering(EpisodeSortKey.FILENAME, ascending = true),
        repository.observeOrdering("podcast-2").first(),
      )
    }

  @Test
  fun `unknown sort key falls back to the default key instead of failing`() =
    runBlocking {
      dao.upsert(ItemPreferenceEntity(itemId = "podcast-1", sortKey = "MYSTERY_KEY", sortAscending = false))

      val ordering = repository.observeOrdering("podcast-1").first()

      assertEquals(EpisodeOrdering.DEFAULT.key, ordering.key)
      assertEquals(false, ordering.ascending)
    }
}
