package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.components.AsyncShimmeringImage

data class DownloadInfo(
    val id: String,
    val title: String,
    val author: String?,
    val chaptersDownloaded: Int,
    val chaptersTotal: Int,
    val sizeMb: Int,
)

@Composable
private fun DownloadItemComposable(
    info: DownloadInfo,
    imageLoader: ImageLoader,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(info.id) {
        ImageRequest.Builder(context)
            .data(info.id)
            .size(coil.size.Size.ORIGINAL)
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncShimmeringImage(
            imageRequest = imageRequest,
            imageLoader = imageLoader,
            contentDescription = "${info.title} cover",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(64.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
            error = painterResource(R.drawable.cover_fallback),
        )

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            info.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(16.dp)
                )
                Text(
                    text = "${info.sizeMb} MB",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier
                )
            }

        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagementScreen(
    onBack: () -> Unit,
    imageLoader: ImageLoader,
) {
    val downloads = remember {
        listOf(
            DownloadInfo("war_and_peace", "War and Peace", "Leo Tolstoy", 120, 361, 1412),
            DownloadInfo("crime_and_punishment", "Crime and Punishment", "Fyodor Dostoevsky", 45, 185, 502),
            DownloadInfo("the_brothers_karamazov", "The Brothers Karamazov", "Fyodor Dostoevsky", 80, 280, 950),
            DownloadInfo("pride_and_prejudice", "Pride and Prejudice", "Jane Austen", 35, 61, 210),
            DownloadInfo("jane_eyre", "Jane Eyre", "Charlotte Brontë", 42, 72, 265),
            DownloadInfo("wuthering_heights", "Wuthering Heights", "Emily Brontë", 30, 56, 223),
            DownloadInfo("moby_dick", "Moby-Dick", "Herman Melville", 95, 135, 740),
            DownloadInfo("the_great_gatsby", "The Great Gatsby", "F. Scott Fitzgerald", 9, 21, 134),
            DownloadInfo("to_kill_a_mockingbird", "To Kill a Mockingbird", "Harper Lee", 15, 31, 142),
            DownloadInfo("1984", "1984", "George Orwell", 19, 24, 168),
            DownloadInfo("brave_new_world", "Brave New World", "Aldous Huxley", 18, 27, 156),
            DownloadInfo("the_stranger", "The Stranger", "Albert Camus", 10, 19, 104),
            DownloadInfo("the_trial", "The Trial", "Franz Kafka", 16, 32, 152),
            DownloadInfo("don_quixote", "Don Quixote", "Miguel de Cervantes", 130, 302, 1214),
            DownloadInfo("les_miserables", "Les Misérables", "Victor Hugo", 150, 365, 1432),
            DownloadInfo("the_count_of_monte_cristo", "The Count of Monte Cristo", "Alexandre Dumas", 145, 328, 1325),
            DownloadInfo("the_picture_of_dorian_gray", "The Picture of Dorian Gray", "Oscar Wilde", 20, 32, 148),
            DownloadInfo("the_old_man_and_the_sea", "The Old Man and the Sea", "Ernest Hemingway", 8, 12, 89),
            DownloadInfo("faust", "Faust", "Johann Wolfgang von Goethe", 24, 54, 233),
            DownloadInfo("dracula", "Dracula", "Bram Stoker", 33, 65, 275),
        )

    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxHeight(),
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(
                count = downloads.size,
                key = { "download_item_${downloads[it].id}" },
            ) { index ->
                DownloadItemComposable(
                    info = downloads[index],
                    imageLoader = imageLoader,
                    onRemove = { },
                )
            }
        }
    }
}
