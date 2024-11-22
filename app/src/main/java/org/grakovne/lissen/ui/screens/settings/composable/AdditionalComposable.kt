package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.BuildConfig

@Composable
fun AdditionalComposable() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = colorScheme.onSurface.copy(alpha = 0.2f),
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clickable { uriHandler.openUri("https://github.com/GrakovNe/lissen-android") }
                .align(Alignment.CenterHorizontally),
            text = "Lissen v ${BuildConfig.VERSION_NAME}",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .align(Alignment.CenterHorizontally),
            text = "© 2024 Max Grakov. MIT License",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
