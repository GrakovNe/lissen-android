package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter

/**
 * A position inside an item expressed independently of the chapter order:
 * the chapter it falls into and the offset from that chapter's start.
 */
data class ChapterLocation(
  val chapterId: String,
  val offset: Double,
)

/**
 * The single place that knows how chapters are ordered.
 *
 * Every consumer of a [DetailedItem] relies on `chapters` and `files` being in the same order
 * and on `start`/`end` being monotonic. Reordering therefore always goes through [reorder],
 * which permutes both lists in lockstep, recomputes the bounds and carries the progress over.
 *
 * The canonical order is the one the channel converters produce, encoded in
 * [PlayingChapter.index]. Ordering is a pure function of chapter keys, so it can be applied to
 * an item coming from the network or from the cache, in any input order, with the same result.
 */
object ChapterOrdering {
  fun canonical(item: DetailedItem): DetailedItem = reorder(item, compareBy { it.index })

  fun apply(
    item: DetailedItem,
    configuration: EpisodeOrderingConfiguration?,
  ): DetailedItem = reorder(item, comparator(configuration))

  fun locate(
    item: DetailedItem,
    position: Double,
  ): ChapterLocation? {
    val chapters = item.chapters
    if (chapters.isEmpty()) return null

    val chapter = chapters.firstOrNull { position < it.end } ?: chapters.last()
    val offset = (position - chapter.start).coerceIn(0.0, chapter.duration)

    return ChapterLocation(chapterId = chapter.id, offset = offset)
  }

  fun position(
    item: DetailedItem,
    location: ChapterLocation,
  ): Double? =
    item
      .chapters
      .firstOrNull { it.id == location.chapterId }
      ?.let { it.start + location.offset.coerceIn(0.0, it.duration) }

  /**
   * Translates a position expressed in the order of [from] into the order of [to].
   * Falls back to the raw value when the chapter cannot be found on either side.
   */
  fun translate(
    from: DetailedItem,
    to: DetailedItem,
    position: Double,
  ): Double =
    locate(from, position)
      ?.let { position(to, it) }
      ?: position

  fun toCanonicalPosition(
    item: DetailedItem,
    position: Double,
  ): Double = translate(item, canonical(item), position)

  fun fromCanonicalPosition(
    item: DetailedItem,
    canonicalPosition: Double,
  ): Double = translate(canonical(item), item, canonicalPosition)

  fun comparator(configuration: EpisodeOrderingConfiguration?): Comparator<PlayingChapter> {
    val config = configuration ?: return defaultComparator

    val primary = keyComparator(config.option)
    val tieBreakers =
      listOf(EpisodeOrderingOption.SEASON, EpisodeOrderingOption.EPISODE, EpisodeOrderingOption.TITLE)
        .filterNot { it == config.option }
        .map { keyComparator(it) }

    val chain =
      (listOf(primary) + tieBreakers)
        .reduce { acc, next -> acc.then(next) }
        .then(compareBy { it.index })

    return when (config.direction) {
      LibraryOrderingDirection.ASCENDING -> chain
      LibraryOrderingDirection.DESCENDING -> chain.reversed()
    }
  }

  private val defaultComparator: Comparator<PlayingChapter> =
    compareBy<PlayingChapter>({ it.publishedAt }, { it.season.asNumber() }, { it.episode.asNumber() }, { it.index })

  private fun keyComparator(option: EpisodeOrderingOption): Comparator<PlayingChapter> =
    when (option) {
      EpisodeOrderingOption.PUBLISHED_AT -> compareBy { it.publishedAt }
      EpisodeOrderingOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
      EpisodeOrderingOption.SEASON -> compareBy { it.season.asNumber() }
      EpisodeOrderingOption.EPISODE -> compareBy { it.episode.asNumber() }
      EpisodeOrderingOption.FILE_NAME -> compareBy(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { it.fileName }
    }

  private fun reorder(
    item: DetailedItem,
    comparator: Comparator<PlayingChapter>,
  ): DetailedItem {
    val chapters = item.chapters
    if (chapters.size < 2) return item

    val order = chapters.indices.sortedWith(compareBy(comparator) { chapters[it] })
    if (order == chapters.indices.toList()) return item

    val reorderedChapters = order.map { chapters[it] }.withRecomputedBounds()
    val reorderedFiles =
      when (item.files.size == chapters.size) {
        true -> order.map { item.files[it] }
        false -> item.files
      }

    val reordered = item.copy(chapters = reorderedChapters, files = reorderedFiles)

    val progress =
      item
        .progress
        ?.let { it.copy(currentTime = translate(item, reordered, it.currentTime)) }

    return reordered.copy(progress = progress)
  }

  private fun List<PlayingChapter>.withRecomputedBounds(): List<PlayingChapter> {
    var accumulated = 0.0

    return map { chapter ->
      val start = accumulated
      accumulated += chapter.duration
      chapter.copy(start = start, end = accumulated)
    }
  }

  private fun String?.asNumber(): Int? = this?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
}
