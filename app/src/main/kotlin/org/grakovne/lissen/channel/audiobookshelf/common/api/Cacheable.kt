package org.grakovne.lissen.channel.audiobookshelf.common.api

/**
 * Marks a Retrofit `GET` as conditionally cached. Apply it as a defaulted parameter
 * `@Tag cacheable: Cacheable = Cacheable()`: Retrofit tags the request with this type,
 * the [ConditionalCacheInterceptor] revalidates it with the stored weak `ETag`, and
 * [safeApiCall] serves the cached object on `304 Not Modified`.
 */
class Cacheable
