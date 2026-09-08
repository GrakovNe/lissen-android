package org.grakovne.lissen.channel.audiobookshelf.common.api

/**
 * Marks a Retrofit `GET` as conditionally cached. Apply it with `@Tag(Cacheable::class)`:
 * the [ConditionalCacheInterceptor] revalidates the request with the stored weak
 * `ETag`, and [safeApiCall] serves the cached object on `304 Not Modified`.
 */
class Cacheable
