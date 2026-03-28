package org.grakovne.lissen.lib.domain

private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"

fun String.fixUriScheme(): String {
  val normalized = when {
    startsWith(HTTP_SCHEME) || startsWith(HTTPS_SCHEME) -> this
    else -> HTTP_SCHEME + this
  }
  
  return if (normalized.endsWith('/')) normalized else "$normalized/"
}