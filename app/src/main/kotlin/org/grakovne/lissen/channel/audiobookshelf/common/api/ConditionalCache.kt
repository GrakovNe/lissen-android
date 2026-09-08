package org.grakovne.lissen.channel.audiobookshelf.common.api

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory store behind [Cacheable] endpoints, keyed by request URL. Each entry
 * holds the last deserialized object and its weak `ETag`; the [ConditionalCacheInterceptor]
 * reads the validator to revalidate, and [safeApiCall] writes the fresh object.
 *
 * The store is a weighted LRU: every entry carries a weight ([sizeOf]) and the total
 * is kept under [maxWeight] by evicting the least-recently-used entries first. Reads
 * count as use, so a resource served even on a `304` stays hot while cold pages fall
 * out. Nothing is persisted; [invalidateAll] drops everything on an identity change.
 */
@Singleton
class ConditionalCache
  internal constructor(
    private val maxWeight: Int,
  ) {
    @Inject
    constructor() : this(DEFAULT_MAX_WEIGHT)

    // accessOrder = true: a get() moves the entry to the most-recently-used tail, so
    // iteration order is least-recently-used first, which is exactly the eviction order.
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var totalWeight = 0

    // Per-class list of the collection-valued fields used by the reflective weight
    // estimate; resolved once per type so a cache write does no field discovery.
    private val collectionFields = ConcurrentHashMap<Class<*>, List<Field>>()

    @Synchronized
    fun etag(url: String): String? = entries[url]?.etag

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> value(url: String): T? = entries[url]?.value as T?

    @Synchronized
    fun put(
      url: String,
      value: Any?,
      etag: String?,
    ) {
      entries.remove(url)?.let { totalWeight -= it.weight }

      val weight = sizeOf(value)
      entries[url] = Entry(value, etag, weight)
      totalWeight += weight

      evictToBudget()
    }

    @Synchronized
    fun invalidate(url: String) {
      entries.remove(url)?.let { totalWeight -= it.weight }
    }

    @Synchronized
    fun invalidateAll() {
      entries.clear()
      totalWeight = 0
    }

    private fun evictToBudget() {
      val iterator = entries.entries.iterator()
      while (totalWeight > maxWeight && iterator.hasNext()) {
        totalWeight -= iterator.next().value.weight
        iterator.remove()
      }
    }

    /**
     * Approximate footprint of a cached object, measured as the number of elements in
     * its top-level collections. These responses wrap one big list (`results`,
     * `mediaProgress`, ...), and within a single endpoint the element size is roughly
     * constant, so element count tracks retained memory while allocating nothing per
     * element — unlike `toString()`, which materializes the whole graph just to measure
     * it. The set of collection fields is resolved once per class and cached. Measured
     * on write and stored on the entry; [maxWeight] is a budget in elements.
     */
    private fun sizeOf(value: Any?): Int {
      if (value == null) return 1
      val direct = collectionSize(value)
      if (direct >= 0) return direct.coerceAtLeast(1)

      var total = 0
      for (field in collectionFields(value.javaClass)) {
        val element =
          try {
            field.get(value)
          } catch (e: Throwable) {
            null
          }
        val size = collectionSize(element)
        if (size > 0) total += size
      }
      return total.coerceAtLeast(1)
    }

    // Size of a collection-like value, or -1 when it is not one.
    private fun collectionSize(value: Any?): Int =
      when (value) {
        is Collection<*> -> value.size
        is Map<*, *> -> value.size
        is Array<*> -> value.size
        else -> -1
      }

    private fun collectionFields(type: Class<*>): List<Field> =
      collectionFields.computeIfAbsent(type) { clazz ->
        buildList {
          var current: Class<*>? = clazz
          while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
              if (Modifier.isStatic(field.modifiers)) continue
              val fieldType = field.type
              val isCollectionLike =
                Collection::class.java.isAssignableFrom(fieldType) ||
                  Map::class.java.isAssignableFrom(fieldType) ||
                  fieldType.isArray
              if (!isCollectionLike) continue
              // JDK-internal classes (e.g. java.lang.String's byte[]) reject setAccessible;
              // skip such fields rather than let the estimate throw on a cache write.
              val accessible =
                try {
                  field.isAccessible = true
                  true
                } catch (e: Throwable) {
                  false
                }
              if (accessible) add(field)
            }
            current = current.superclass
          }
        }
      }

    private class Entry(
      val value: Any?,
      val etag: String?,
      val weight: Int,
    )

    private companion object {
      // Budget in elements (see [sizeOf]). A library page is a few dozen to a few
      // hundred items and [sizeOf] counts elements, so this holds on the order of a
      // handful of pages plus user state. Tunable; retained heap is a multiple of it.
      const val DEFAULT_MAX_WEIGHT = 2000
    }
  }
