package org.grakovne.lissen.lib.domain

fun String.fixUriScheme() = when (this.startsWith("http://") || this.startsWith("https://")) {
  true -> this
  false -> "http://$this"
}