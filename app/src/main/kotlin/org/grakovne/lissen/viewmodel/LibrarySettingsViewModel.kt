package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LibrarySettingsViewModel
  @Inject
  constructor(
    private val mediaChannel: LissenMediaProvider,
    private val library: LibraryPreferences,
  ) : ViewModel() {
    private val _libraries = MutableStateFlow<List<Library>>(emptyList())
    val libraries: StateFlow<List<Library>> = _libraries.asStateFlow()

    private val _preferredLibrary = MutableStateFlow<Library?>(library.getPreferredLibrary())
    val preferredLibrary: StateFlow<Library?> = _preferredLibrary.asStateFlow()

    private val _preferredLibraryOrdering = MutableStateFlow(library.getLibraryOrdering())
    val preferredLibraryOrdering: StateFlow<LibraryOrderingConfiguration> = _preferredLibraryOrdering.asStateFlow()

    val preferredLibraryType: StateFlow<LibraryType> =
      library.preferredLibraryTypeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), library.getPreferredLibraryType())

    val hideCompleted = library.hideCompletedFlow

    val libraryGrouping = library.libraryGroupingFlow

    fun fetchLibraries() {
      viewModelScope.launch {
        when (val response = mediaChannel.fetchLibraries()) {
          is OperationResult.Success -> {
            val libraries = response.data
            _libraries.value = libraries

            val preferredLibrary = library.getPreferredLibrary()

            _preferredLibrary.value =
              when (preferredLibrary) {
                null -> libraries.firstOrNull()
                else -> libraries.find { it.id == preferredLibrary.id }
              }
          }

          is OperationResult.Error -> {
            _libraries.value = library.getPreferredLibrary()?.let { listOf(it) } ?: emptyList()
          }
        }
      }
    }

    fun fetchPreferredLibraryId(): String = library.getPreferredLibrary()?.id ?: ""

    fun fetchLibraryOrdering(): LibraryOrderingConfiguration = library.getLibraryOrdering()

    fun preferLibrary(library: Library) {
      Timber.d("User action: preferLibrary ${library.id} '${library.title}'")
      _preferredLibrary.value = library
      this.library.savePreferredLibrary(library)
    }

    fun preferLibraryOrdering(configuration: LibraryOrderingConfiguration) {
      Timber.d("User action: preferLibraryOrdering $configuration")
      _preferredLibraryOrdering.value = configuration
      library.saveLibraryOrdering(configuration)
    }

    fun toggleHideCompleted() {
      Timber.d("User action: toggleHideCompleted (current=${library.getHideCompleted()})")
      library.saveHideCompleted(library.getHideCompleted().not())
    }

    fun preferLibraryGrouping(grouping: LibraryGrouping) {
      Timber.d("User action: preferLibraryGrouping $grouping")
      library.saveLibraryGrouping(grouping)
    }
  }
