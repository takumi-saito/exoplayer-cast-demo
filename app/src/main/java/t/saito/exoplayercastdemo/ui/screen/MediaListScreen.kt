package t.saito.exoplayercastdemo.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import t.saito.exoplayercastdemo.expandedcontrols.ExpandedControlsActivity
import t.saito.exoplayercastdemo.ui.component.MediaListItem
import t.saito.exoplayercastdemo.ui.component.MiniPlayer
import t.saito.exoplayercastdemo.viewmodel.MediaViewModel
import t.saito.exoplayercastdemo.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    mediaViewModel: MediaViewModel,
    playerViewModel: PlayerViewModel,
    onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mediaList by mediaViewModel.mediaList.collectAsState()
    val isLoading by mediaViewModel.isLoading.collectAsState()
    val error by mediaViewModel.error.collectAsState()

    val currentMedia by playerViewModel.currentMedia.collectAsState()
    val playbackState by playerViewModel.playbackState.collectAsState()
    val isCasting by playerViewModel.isCasting.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Media Player") }
            )
        },
        bottomBar = {
            MiniPlayer(
                currentMedia = currentMedia,
                playbackState = playbackState,
                isCasting = isCasting,
                onTogglePlayPause = { playerViewModel.togglePlayPause() },
                onExpand = {
                    // Cast中はExpandedControlsActivityを起動
                    // ローカル再生時はFullPlayerScreenへ遷移
                    if (isCasting) {
                        context.startActivity(
                            Intent(context, ExpandedControlsActivity::class.java)
                        )
                    } else {
                        onExpandPlayer()
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
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                error != null -> {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                mediaList.isEmpty() -> {
                    Text(
                        text = "No media found",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(mediaList) { mediaItem ->
                            MediaListItem(
                                mediaItem = mediaItem,
                                onClick = { playerViewModel.playMedia(mediaItem) }
                            )
                        }
                    }
                }
            }
        }
    }
}
