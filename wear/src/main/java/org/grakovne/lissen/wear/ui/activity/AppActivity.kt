package org.grakovne.lissen.wear.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.dialog
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.nav.SwipeDismissableNavHost
import com.google.android.horologist.compose.nav.composable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.grakovne.lissen.wear.presentation.theme.LissenTheme
import org.grakovne.lissen.wear.ui.navigation.LissenNavigation
import org.grakovne.lissen.wear.ui.screens.chapters.ChaptersScreen
import org.grakovne.lissen.wear.ui.screens.library.LibraryScreen
import org.grakovne.lissen.wear.ui.screens.player.PlayerScreen

@AndroidEntryPoint
class AppActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()

    super.onCreate(savedInstanceState)

    setTheme(android.R.style.Theme_DeviceDefault)

    setContent {
      WearApp()
    }
  }
}

enum class HorizontalPages {
  LIBRARY,
  PLAYER,
  CHAPTERS
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun WearApp() {
  val navController = rememberSwipeDismissableNavController()

  val pagerState = rememberPagerState(
    initialPage = HorizontalPages.PLAYER.ordinal
  ) { HorizontalPages.entries.size }

  val scope = rememberCoroutineScope()

  LissenTheme {
    AppScaffold() {
      SwipeDismissableNavHost(
        navController = navController,
        startDestination = LissenNavigation.Player
      ) {
        composable<LissenNavigation.Player> {
          HorizontalPagerScaffold(
            pagerState = pagerState,
          ) {
            HorizontalPager(
              state = pagerState,
            ) { page ->
              AnimatedPage(
                pageIndex = page,
                pagerState = pagerState
              ) {
                when (HorizontalPages.entries[page]) {
                  HorizontalPages.LIBRARY -> LibraryScreen(
                    onBookSelected = {
                      scope.launch {
                        pagerState.animateScrollToPage(HorizontalPages.PLAYER.ordinal)
                      }
                    }
                  )
                  HorizontalPages.PLAYER -> PlayerScreen()
                  HorizontalPages.CHAPTERS -> ChaptersScreen(
//                    onChapterSelect = {
//                      scope.launch {
//                        pagerState.animateScrollToPage(HorizontalPages.PLAYER.ordinal)
//                      }
//                    }
                  )
                }
              }
            }
          }
        }

        dialog<LissenNavigation.Settings> {

        }
      }
    }
  }
}
