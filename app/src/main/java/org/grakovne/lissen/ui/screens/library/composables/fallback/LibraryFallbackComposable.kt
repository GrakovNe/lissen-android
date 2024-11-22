package org.grakovne.lissen.ui.screens.library.composables.fallback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.viewmodel.CachingModelView

@Composable
fun LibraryFallbackComposable(
    searchRequested: Boolean,
    cachingModelView: CachingModelView,
    networkQualityService: NetworkQualityService,
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(screenHeight / 2)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val hasNetwork = networkQualityService.isNetworkAvailable()
            val isLocalCache = cachingModelView.localCacheUsing()

            val text = when {
                searchRequested -> null
                isLocalCache -> stringResource(R.string.the_offline_library_is_empty)
                hasNetwork.not() -> stringResource(R.string.no_internet_connection)
                else -> stringResource(R.string.the_library_is_empty)
            }

            val icon = when {
                searchRequested -> null
                isLocalCache -> Icons.AutoMirrored.Filled.LibraryBooks
                hasNetwork.not() -> Icons.Filled.WifiOff
                else -> Icons.AutoMirrored.Filled.LibraryBooks
            }

            icon?.let {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = "Library placeholder",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            text?.let {
                Text(
                    textAlign = TextAlign.Center,
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 36.dp),
                )
            }
        }
    }
}
