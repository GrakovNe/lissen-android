package org.grakovne.lissen.content.ordering

import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** A position independent of the chapter order: the chapter and the offset inside it. */
data class ChapterLocation(
  val chapterId: String,
  val offset: Double,
)

/**
 * Chapters and files are permuted in lockstep, bounds recomputed, progress carried over.
 * The canonical order (published date, season, episode, incoming position; nulls first, exactly
 * as every earlier version sorted) is what stored progress and bookmarks are expressed in, so it
 * must never change.
 */
object ChapterOrdering {
  fun canonical(item: DetailedItem): DetailedItem = reorder(item, defaultComparator, canonicalize = true)

  fun apply(
    item: DetailedItem,
    configuration: EpisodeOrderingConfiguration?,
  ): DetailedItem = reorder(item, comparator(configuration))

  /** A boundary belongs to the chapter starting there; the very end is the end of the last chapter; past the end is nothing. */
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

  /** The end of the item is the end in any order: as a location it would become the start of another chapter. Falls back to the raw value. */
  fun translate(
    from: DetailedItem,
    to: DetailedItem,
    position: Double,
  ): Double {
    val fromEnd = from.end()
    if (fromEnd != null && position >= fromEnd) return to.end() ?: position

    return locate(from, position)
      ?.let { position(to, it) }
      ?: position
  }

  /** A live position may overshoot the declared end by a few hundred ms: that is the end, not outside. */
  fun toCanonicalPosition(
    item: DetailedItem,
    position: Double,
  ): Double = translate(item, canonical(item), position.coerceAtMost(item.end() ?: position))

  fun fromCanonicalPosition(
    item: DetailedItem,
    canonicalPosition: Double,
  ): Double = translate(canonical(item), item, canonicalPosition)

  fun DetailedItem.end(): Double? = chapters.lastOrNull()?.end

  /** Storage keeps whole seconds; truncating can cross into the neighbouring canonical episode, so the second is chosen strictly inside the chapter. */
  fun storedBookmarkPosition(
    item: DetailedItem,
    position: Double,
  ): Double {
    val canonical = canonical(item)
    val pinned = position.coerceAtMost(item.end() ?: position)
    val exact = translate(item, canonical, pinned)
    val chapter =
      locate(item, pinned)
        ?.let { location -> canonical.chapters.firstOrNull { it.id == location.chapterId } }
        ?: return floor(exact)

    val lowest = ceil(chapter.start)
    val highest = ceil(chapter.end) - 1

    return when (highest >= lowest) {
      true -> floor(exact).coerceIn(lowest, highest)
      false -> floor(exact)
    }
  }

  fun isReorderable(item: DetailedItem): Boolean = item.isPermutable()

  /** Primary key, then season and episode, then the incoming position; the default configuration is the canonical order. */
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
   * Only an item whose chapters are its files is permuted: a chaptered book's bounds are file
   * offsets and rewriting them would play the wrong audio. Bounds are recomputed only when a
   * permutation happens. With [canonicalize] index = position; all-zero indices come from an older
   * version and are taken from the list order, which for such an item is canonical.
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

  /** Whole numbers only, as always compared: "S03" or "1.5" sort first as null. */
  private fun String?.asNumber(): Int? = this?.takeIf { it.isNotBlank() }?.toIntOrNull()

  private const val DURATION_EPSILON = 1e-3
}
