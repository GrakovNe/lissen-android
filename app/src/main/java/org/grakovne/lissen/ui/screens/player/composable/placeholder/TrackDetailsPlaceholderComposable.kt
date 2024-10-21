package org.grakovne.lissen.ui.screens.player.composable.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import org.grakovne.lissen.R

@Composable
fun TrackDetailsPlaceholderComposable(
    bookTitle: String?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
                .background(Color.Gray)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = bookTitle ?: "",
            style = typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.player_screen_now_playing_title_chapter_of, 100, "1000"),
            style = typography.bodyMedium,
            color = Color.Transparent,
            modifier = Modifier
                .shimmer()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray)
        )
    }
}
