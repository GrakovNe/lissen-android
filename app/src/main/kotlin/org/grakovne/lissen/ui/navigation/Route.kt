package org.grakovne.lissen.ui.navigation

import android.net.Uri

const val ROUTE_LIBRARY = "library_screen"
const val ROUTE_PLAYER = "player_screen"
const val ROUTE_SETTINGS = "settings_screen"
const val ROUTE_LOGIN = "login_screen"

const val ROUTE_SETTINGS_CACHED_ITEMS = "$ROUTE_SETTINGS/cached_items"
const val ROUTE_SETTINGS_CACHE = "$ROUTE_SETTINGS/cache_settings"
const val ROUTE_SETTINGS_LOCAL_URL = "$ROUTE_SETTINGS/local_url"
const val ROUTE_SETTINGS_CUSTOM_HEADERS = "$ROUTE_SETTINGS/custom_headers"
const val ROUTE_SETTINGS_CLIENT_CERTIFICATE = "$ROUTE_SETTINGS/client_certificate"
const val ROUTE_SETTINGS_CONNECTION = "$ROUTE_SETTINGS/connection_settings"
const val ROUTE_SETTINGS_ADVANCED = "$ROUTE_SETTINGS/advanced_settings"
const val ROUTE_SETTINGS_SEEK = "$ROUTE_SETTINGS/seek_settings"
const val ROUTE_SETTINGS_PLAYBACK = "$ROUTE_SETTINGS/playback_preferences"
const val ROUTE_SETTINGS_APPEARANCE = "$ROUTE_SETTINGS/appearance_preferences"

const val ARG_LINKED_SEARCH_TOKEN = "linkedSearchToken"
const val ARG_BOOK_ID = "bookId"
const val ARG_BOOK_TITLE = "bookTitle"
const val ARG_BOOK_SUBTITLE = "bookSubtitle"
const val ARG_START_INSTANTLY = "startInstantly"

/** Route patterns with argument placeholders. Used to declare destinations in the NavHost. */
const val ROUTE_LIBRARY_PATTERN =
  "$ROUTE_LIBRARY?$ARG_LINKED_SEARCH_TOKEN={$ARG_LINKED_SEARCH_TOKEN}"

const val ROUTE_PLAYER_PATTERN =
  "$ROUTE_PLAYER/{$ARG_BOOK_ID}" +
    "?$ARG_BOOK_TITLE={$ARG_BOOK_TITLE}" +
    "&$ARG_BOOK_SUBTITLE={$ARG_BOOK_SUBTITLE}" +
    "&$ARG_START_INSTANTLY={$ARG_START_INSTANTLY}"

/**
 * Builds a concrete library route. Arguments are URL-encoded here; the Navigation component
 * decodes them automatically when reading from the back stack entry, so destinations must not
 * decode again.
 */
fun libraryRoute(linkedSearchToken: String? = null): String =
  when (linkedSearchToken) {
    null -> ROUTE_LIBRARY
    else -> "$ROUTE_LIBRARY?$ARG_LINKED_SEARCH_TOKEN=${Uri.encode(linkedSearchToken)}"
  }

/**
 * Builds a concrete player route with all arguments URL-encoded. This is the single place that
 * assembles the player route, so callers never have to escape values by hand.
 */
fun playerRoute(
  bookId: String,
  bookTitle: String,
  bookSubtitle: String?,
  startInstantly: Boolean = false,
): String =
  buildString {
    append("$ROUTE_PLAYER/${Uri.encode(bookId)}")
    append("?$ARG_BOOK_TITLE=${Uri.encode(bookTitle)}")
    append("&$ARG_BOOK_SUBTITLE=${Uri.encode(bookSubtitle ?: "")}")
    append("&$ARG_START_INSTANTLY=$startInstantly")
  }
