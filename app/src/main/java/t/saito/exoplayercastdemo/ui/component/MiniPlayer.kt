package t.saito.exoplayercastdemo.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import t.saito.exoplayercastdemo.data.model.MediaItem
import t.saito.exoplayercastdemo.data.model.PlaybackState

@Composable
fun MiniPlayer(
    currentMedia: MediaItem?,
    playbackState: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentMedia == null) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onExpand() },
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentMedia.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = when (playbackState) {
                        is PlaybackState.Playing -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when (playbackState) {
                        is PlaybackState.Playing -> "Pause"
                        else -> "Play"
                    }
                )
            }
        }
    }
}
