package org.grakovne.lissen.ui.screens.common

import android.content.Context
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.RemainingItemsDownloadOption

fun DownloadOption?.makeText(
  context: Context,
  libraryType: LibraryType,
): String =
  when (this) {
    null -> {
      context.getString(R.string.downloads_menu_download_option_disable)
    }

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
        LibraryType.LIBRARY -> {
          context.resources.getQuantityString(
            R.plurals.downloads_menu_download_option_next_chapters,
            itemsNumber,
            itemsNumber,
          )
        }

        LibraryType.PODCAST -> {
          context.resources.getQuantityString(
            R.plurals.downloads_menu_download_option_next_episodes,
            itemsNumber,
            itemsNumber,
          )
        }

        LibraryType.UNKNOWN -> {
          context.resources.getQuantityString(
            R.plurals.downloads_menu_download_option_next_items,
            itemsNumber,
            itemsNumber,
          )
        }
      }
    }
  }
