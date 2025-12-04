package t.saito.exoplayercastdemo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import t.saito.exoplayercastdemo.data.model.PlaybackState
import t.saito.exoplayercastdemo.service.QueueManager

@Composable
fun PlayerControls(
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onSeek: (Long) -> Unit,
    // キュー関連（オプション）
    hasQueue: Boolean = false,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    repeatMode: QueueManager.RepeatMode = QueueManager.RepeatMode.OFF,
    shuffleEnabled: Boolean = false,
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Seek bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() else 0f,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = duration > 0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // シャッフルボタン（キューがある場合のみ表示）
            if (hasQueue) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 前へスキップボタン（キューがある場合のみ表示）
            if (hasQueue) {
                IconButton(
                    onClick = onSkipPrevious,
                    enabled = hasPrevious,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 再生/一時停止ボタン
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = when (playbackState) {
                        is PlaybackState.Playing -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when (playbackState) {
                        is PlaybackState.Playing -> "Pause"
                        else -> "Play"
                    },
                    modifier = Modifier.size(48.dp)
                )
            }

            // 次へスキップボタン（キューがある場合のみ表示）
            if (hasQueue) {
                IconButton(
                    onClick = onSkipNext,
                    enabled = hasNext,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // リピートボタン（キューがある場合のみ表示）
            if (hasQueue) {
                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            QueueManager.RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != QueueManager.RepeatMode.OFF)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 停止ボタン（キューがない場合のみ表示、またはコンパクト表示）
            if (!hasQueue) {
                IconButton(
                    onClick = onStopClick,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop"
                    )
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val seconds = (timeMs / 1000) % 60
    val minutes = (timeMs / (1000 * 60)) % 60
    val hours = (timeMs / (1000 * 60 * 60))

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
