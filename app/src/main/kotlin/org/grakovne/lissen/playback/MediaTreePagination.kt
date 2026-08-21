package org.grakovne.lissen.playback

internal fun <T> List<T>.page(
  page: Int,
  pageSize: Int,
): List<T> {
  if (page < 0 || pageSize <= 0) return emptyList()

  val from = page.toLong() * pageSize.toLong()
  if (from >= size) return emptyList()

  val to = (from + pageSize).coerceAtMost(size.toLong())
  return subList(from.toInt(), to.toInt())
}
