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
 * The canonical order is the default one: published date, season, episode, then the position
 * the chapter came in with ([PlayingChapter.index]). It is the order every version of the app
 * has shown podcasts in, so positions stored on the server and in the cache (progress,
 * bookmarks) are canonical positions and stay valid across upgrades. For items without
 * ordering keys (books, local-file podcasts) the canonical order is the server order.
 * Ordering is a pure function of chapter keys, so applying it to an item coming from the
 * network or from the cache, in any input order, gives the same result.
 */
object ChapterOrdering {
  fun canonical(item: DetailedItem): DetailedItem = reorder(item, defaultComparator)

  fun apply(
    item: DetailedItem,
    configuration: EpisodeOrderingConfiguration?,
  ): DetailedItem = reorder(item, comparator(configuration))

  /**
   * Resolves a position to a chapter and an offset. A position on a chapter boundary is the
   * start of the chapter that begins there, so chapter starts (where playback lands after an
   * auto-advance) survive a round trip through [position] in any order; the very end of the
   * item is the end of its last chapter. Positions past the end resolve to nothing: they are
   * not real positions and must not be turned into one by a translation.
   */
  fun locate(
    item: DetailedItem,
    position: Double,
  ): ChapterLocation? {
    val chapters = item.chapters
    val last = chapters.lastOrNull() ?: return null
    if (position > last.end) return null

    val chapter = chapters.firstOrNull { position < it.end } ?: last
    val offset = (position - chapter.start).coerceIn(0.0, chapter.duration.coerceAtLeast(0.0))

    return ChapterLocation(chapterId = chapter.id, offset = offset)
  }

  fun position(
    item: DetailedItem,
    location: ChapterLocation,
  ): Double? =
    item
      .chapters
      .firstOrNull { it.id == location.chapterId }
      ?.let { it.start + location.offset.coerceIn(0.0, it.duration.coerceAtLeast(0.0)) }

  /**
   * Translates a position expressed in the order of [from] into the order of [to].
   * Falls back to the raw value when the position is outside the item or the chapter
   * cannot be found on either side.
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

  /**
   * Primary key, then season and episode as tie-breakers, then the incoming position. The
   * default configuration builds exactly the canonical order, so picking it in the UI is the
   * same as having no configuration at all.
   */
  private fun comparator(configuration: EpisodeOrderingConfiguration?): Comparator<PlayingChapter> {
    val config = configuration ?: return defaultComparator

    val primary = keyComparator(config.option)
    val tieBreakers =
      listOf(EpisodeOrderingOption.SEASON, EpisodeOrderingOption.EPISODE)
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

  private val defaultComparator: Comparator<PlayingChapter> by lazy {
    comparator(EpisodeOrderingConfiguration.default)
  }

  private fun keyComparator(option: EpisodeOrderingOption): Comparator<PlayingChapter> =
    when (option) {
      EpisodeOrderingOption.PUBLISHED_AT -> compareBy { it.publishedAt }
      EpisodeOrderingOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
      EpisodeOrderingOption.SEASON -> compareBy(nullsLast()) { it.season.asNumber() }
      EpisodeOrderingOption.EPISODE -> compareBy(nullsLast()) { it.episode.asNumber() }
      EpisodeOrderingOption.FILE_NAME -> compareBy(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { it.fileName }
    }

  /**
   * Chapters and files can only be permuted together. An item whose files do not map one to
   * one onto its chapters (a book with chapter markers over a different number of audio files)
   * is left untouched: reordering its chapters alone would play the wrong audio under every title.
   */
  private fun reorder(
    item: DetailedItem,
    comparator: Comparator<PlayingChapter>,
  ): DetailedItem {
    val chapters = item.chapters
    if (chapters.size < 2) return item

    val order = chapters.indices.sortedWith(compareBy(comparator) { chapters[it] })
    if (order == chapters.indices.toList()) return item
    if (item.files.size != chapters.size) return item

    val reorderedChapters = order.map { chapters[it] }.withRecomputedBounds()
    val reorderedFiles = order.map { item.files[it] }

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

  /**
   * Season and episode numbers as feeds actually write them: "3", "S03", "1.5", "12a".
   * The leading number is what counts; values without one sort after the numbered ones.
   */
  private fun String?.asNumber(): Int? = this?.let { LEADING_NUMBER.find(it)?.value?.toIntOrNull() }

  private val LEADING_NUMBER = Regex("\\d+")
}
