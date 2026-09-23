package org.grakovne.lissen.channel.audiobookshelf.common.api

import androidx.collection.LruCache
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** LRU store of the last object and its weak ETag per URL, weighted by [sizeOf]; nothing is persisted. */
@Singleton
class ConditionalCache
  internal constructor(
    maxWeight: Int,
  ) {
    @Inject
    constructor() : this(DEFAULT_MAX_WEIGHT)

    // weighed once on write: LruCache must never re-measure a value that mutated
    private val entries =
      object : LruCache<String, Entry>(maxWeight) {
        override fun sizeOf(
          key: String,
          value: Entry,
        ): Int = value.weight
      }

    private val collectionFields = ConcurrentHashMap<Class<*>, List<Field>>()

    fun etag(url: String): String? = entries.get(url)?.etag

    @Suppress("UNCHECKED_CAST")
    fun <T> value(url: String): T? = entries.get(url)?.value as T?

    fun put(
      url: String,
      value: Any?,
      etag: String?,
    ) {
      entries.put(url, Entry(value, etag, sizeOf(value)))
    }

    fun invalidate(url: String) {
      entries.remove(url)
    }

    fun invalidateAll() {
      entries.evictAll()
    }

    /** Element count of the top-level collections: tracks retained memory without allocating, unlike toString(). */
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
              // JDK-internal classes reject setAccessible: skip rather than throw on a cache write
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
      // in elements, see sizeOf: a handful of library pages plus user state
      const val DEFAULT_MAX_WEIGHT = 2000
    }
  }
