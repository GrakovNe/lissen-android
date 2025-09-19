package org.grakovne.lissen.ui.screens.common

import android.content.Context
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.AllItemsDownloadOption
import org.grakovne.lissen.lib.domain.CurrentItemDownloadOption
import org.grakovne.lissen.lib.domain.DownloadOption
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.NumberItemDownloadOption
import org.grakovne.lissen.lib.domain.RemainingItemsDownloadOption

fun DownloadOption?.makeText(
  context: Context,
  libraryType: LibraryType,
): String =
  when (this) {
    null -> context.getString(R.string.downloads_menu_download_option_disable)

    CurrentItemDownloadOption -> {
      when (libraryType) {
        LibraryType.LIBRARY -> context.getString(R.string.downloads_menu_download_option_current_chapter)
        LibraryType.PODCAST -> context.getString(R.string.downloads_menu_download_option_current_episode)
        LibraryType.UNKNOWN -> context.getString(R.string.downloads_menu_download_option_current_item)
      }
    }

    AllItemsDownloadOption -> {
      when (libraryType) {
        LibraryType.LIBRARY -> context.getString(R.string.downloads_menu_download_option_entire_book)
        LibraryType.PODCAST -> context.getString(R.string.downloads_menu_download_option_entire_podcast)
        LibraryType.UNKNOWN -> context.getString(R.string.downloads_menu_download_option_entire_item)
      }
    }

    RemainingItemsDownloadOption -> {
      when (libraryType) {
        LibraryType.LIBRARY -> context.getString(R.string.downloads_menu_download_option_remaining_chapters)
        LibraryType.PODCAST -> context.getString(R.string.downloads_menu_download_option_remaining_episodes)
        LibraryType.UNKNOWN -> context.getString(R.string.downloads_menu_download_option_remaining_items)
      }
    }

    is NumberItemDownloadOption -> {
      when (libraryType) {
        LibraryType.LIBRARY ->
          context.getString(
            R.string.downloads_menu_download_option_next_chapters,
            itemsNumber,
          )

        LibraryType.PODCAST ->
          context.getString(
            R.string.downloads_menu_download_option_next_episodes,
            itemsNumber,
          )

        LibraryType.UNKNOWN ->
          context.getString(
            R.string.downloads_menu_download_option_next_items,
            itemsNumber,
          )
      }
    }
  }
