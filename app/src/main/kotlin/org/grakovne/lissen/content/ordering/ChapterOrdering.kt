package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import kotlin.math.abs

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
 * The canonical order is the default one: published date, season, episode (compared exactly
 * as every earlier version of the app compared them: nulls first, season and episode as whole
 * numbers or nothing), then the position the chapter came in with ([PlayingChapter.index]).
 * It is the order every version of the app has shown podcasts in, so positions stored on the
 * server and in the cache (progress, bookmarks) are canonical positions and stay valid across
 * upgrades. For items without ordering keys (books, local-file podcasts) the canonical order
 * is the server order. A canonical item carries `index` = its canonical position, so an item
 * from the cache and the same item from the network order identically under any configuration.
 */
object ChapterOrdering {
  fun canonical(item: DetailedItem): DetailedItem = reorder(item, defaultComparator, canonicalize = true)

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

  /**
   * A live position can run a few hundred ms past the declared end of the item (files are
   * often a little longer than the server says); that is the end of the item, not a position
   * outside it, so it is pinned to the end before the translation.
   */
  fun toCanonicalPosition(
    item: DetailedItem,
    position: Double,
  ): Double = translate(item, canonical(item), position.coerceAtMost(item.end() ?: position))

  fun fromCanonicalPosition(
    item: DetailedItem,
    canonicalPosition: Double,
  ): Double = translate(canonical(item), item, canonicalPosition)

  fun DetailedItem.end(): Double? = chapters.lastOrNull()?.end

  /** Whether the item can be permuted at all, see [reorder]. */
  fun isReorderable(item: DetailedItem): Boolean = item.isPermutable()

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

  // nulls first everywhere, as Kotlin's compareBy does: that is how the app has always sorted
  private fun keyComparator(option: EpisodeOrderingOption): Comparator<PlayingChapter> =
    when (option) {
      EpisodeOrderingOption.PUBLISHED_AT -> compareBy { it.publishedAt }
      EpisodeOrderingOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
      EpisodeOrderingOption.SEASON -> compareBy { it.season.asNumber() }
      EpisodeOrderingOption.EPISODE -> compareBy { it.episode.asNumber() }
      EpisodeOrderingOption.FILE_NAME -> compareBy(nullsFirst(String.CASE_INSENSITIVE_ORDER)) { it.fileName }
    }

  /**
   * Chapters and files can only be permuted together, so only an item whose chapters are its
   * files (one file per chapter, same durations: podcast episodes, or a book split into files
   * without chapter markers) is ever permuted. Anything else, a book with chapter markers above
   * all, is left exactly as the server described it: its bounds are file offsets and rewriting
   * them would play the wrong audio under every title. Bounds are recomputed only when a
   * permutation actually happens; an item that is already in order keeps its bounds.
   *
   * With [canonicalize] the output carries `index` = position. Degenerate indices (an item
   * stored by an app version that did not know them, so every chapter says 0) are taken from
   * the list order, which for such an item is the canonical one.
   */
  private fun reorder(
    item: DetailedItem,
    comparator: Comparator<PlayingChapter>,
    canonicalize: Boolean = false,
  ): DetailedItem {
    val chapters = item.chapters
    if (chapters.isEmpty()) return item

    val keyed =
      when (canonicalize && chapters.hasDegenerateIndices()) {
        true -> chapters.mapIndexed { position, chapter -> chapter.copy(index = position) }
        false -> chapters
      }

    val order = keyed.indices.sortedWith(compareBy(comparator) { keyed[it] })

    if (order == keyed.indices.toList()) {
      return when (canonicalize && chapters.isRenumbered().not()) {
        true -> item.copy(chapters = keyed.mapIndexed { position, chapter -> chapter.copy(index = position) })
        false -> item
      }
    }

    if (item.isPermutable().not()) return item

    val orderedChapters =
      order
        .map { keyed[it] }
        .withRecomputedBounds()
        .let { if (canonicalize) it.mapIndexed { position, chapter -> chapter.copy(index = position) } else it }

    val reordered = item.copy(chapters = orderedChapters, files = order.map { item.files[it] })

    val progress =
      item
        .progress
        ?.let { it.copy(currentTime = translate(item, reordered, it.currentTime)) }

    return reordered.copy(progress = progress)
  }

  private fun DetailedItem.isPermutable(): Boolean =
    files.size == chapters.size &&
      chapters.zip(files).all { (chapter, file) -> abs(chapter.duration - file.duration) < DURATION_EPSILON }

  private fun List<PlayingChapter>.withRecomputedBounds(): List<PlayingChapter> {
    var accumulated = 0.0

    return map { chapter ->
      val start = accumulated
      accumulated += chapter.duration
      chapter.copy(start = start, end = accumulated)
    }
  }

  private fun List<PlayingChapter>.isRenumbered(): Boolean = withIndex().all { (position, chapter) -> chapter.index == position }

  private fun List<PlayingChapter>.hasDegenerateIndices(): Boolean = map { it.index }.toSet().size != size

  /**
   * Season and episode as whole numbers, exactly as they have always been compared: anything
   * else ("S03", "1.5", "bonus", blank) is no number at all and sorts first.
   */
  private fun String?.asNumber(): Int? = this?.takeIf { it.isNotBlank() }?.toIntOrNull()

  private const val DURATION_EPSILON = 1e-3
}
