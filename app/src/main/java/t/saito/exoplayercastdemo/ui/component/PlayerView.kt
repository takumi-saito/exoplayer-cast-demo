package t.saito.exoplayercastdemo.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ui.PlayerView

@Composable
fun PlayerView(
    player: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val playerView = remember {
        PlayerView(context).apply {
            useController = true
            controllerShowTimeoutMs = 3000
            controllerHideOnTouch = true
        }
    }

    DisposableEffect(player) {
        playerView.player = player
        onDispose {
            playerView.player = null
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier
    )
}
