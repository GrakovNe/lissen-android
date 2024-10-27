package org.grakovne.lissen.ui.screens.player.composable

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.ui.theme.ItemAccented

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedComposable(
        currentSpeed: Float ,
        onSpeedChange: (Float) -> Unit,
        onDismissRequest: () -> Unit
) {
    var selectedValue by remember { mutableFloatStateOf(currentSpeed) }
    val values = listOf(1f, 1.25f, 1.5f, 2f, 3f)

    ModalBottomSheet(
            containerColor = Color(0xFFFAFAFA),
            onDismissRequest = onDismissRequest,
            content = {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                            text = "Playback speed",
                            style = typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                            text = "${String.format("%.2f", selectedValue)}x",
                            style = typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                            value = selectedValue,
                            onValueChange = { value ->
                                val snapThreshold = 0.01f
                                val snappedValue = values.find { kotlin.math.abs(it - value) < snapThreshold }
                                        ?: value
                                selectedValue = snappedValue
                                onSpeedChange(snappedValue)
                            },
                            valueRange = 0.5f..3f,
                            modifier = Modifier
                                    .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        values.forEach { value ->
                            Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(48.dp)
                            ) {
                                Button(
                                        onClick = {
                                            selectedValue = value
                                            onSpeedChange(value)
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                                containerColor = when (selectedValue == value) {
                                                    true -> colorScheme.primary
                                                    else -> ItemAccented
                                                }
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                ) {
                                }
                                Text(
                                        text = String.format("%.2f", value),
                                        color = Color.Black,
                                        style = typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
    )
}