package t.saito.exoplayercastdemo.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import t.saito.exoplayercastdemo.data.model.MediaType
import t.saito.exoplayercastdemo.service.PlaybackServiceConnection
import t.saito.exoplayercastdemo.ui.component.PlayerControls
import t.saito.exoplayercastdemo.ui.component.PlayerView
import t.saito.exoplayercastdemo.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    playerViewModel: PlayerViewModel,
    serviceConnection: PlaybackServiceConnection,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMedia by playerViewModel.currentMedia.collectAsState()
    val playbackState by playerViewModel.playbackState.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val service by serviceConnection.service.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentMedia?.title ?: "Player",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentMedia == null) {
                Text(
                    text = "No media selected",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Video player or album art
                    if (currentMedia!!.type == MediaType.VIDEO) {
                        PlayerView(
                            player = service?.getPlayer(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        // Audio player - show media info
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentMedia!!.title,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )

                            currentMedia!!.artist?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Player controls
                    PlayerControls(
                        playbackState = playbackState,
                        currentPosition = currentPosition,
                        duration = duration,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onStopClick = { playerViewModel.stop() },
                        onSeek = { position -> playerViewModel.seekTo(position) },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
