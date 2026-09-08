package org.grakovne.lissen.channel.audiobookshelf.common.api

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Adds `If-None-Match` to requests tagged [Cacheable] when [ConditionalCache] holds a
 * validator for the URL, turning them into revalidate-on-read conditional `GET`s.
 * Untagged requests pass through untouched.
 */
class ConditionalCacheInterceptor(
  private val cache: ConditionalCache,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request: Request = chain.request()
    if (request.tag(Cacheable::class.java) == null) return chain.proceed(request)

    val builder = request.newBuilder()
    cache
      .etag(request.url.toString())
      ?.let { builder.header("If-None-Match", it) }

    return chain.proceed(builder.build())
  }
}
