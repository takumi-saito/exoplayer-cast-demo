package t.saito.exoplayercastdemo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import t.saito.exoplayercastdemo.service.PlaybackServiceConnection
import t.saito.exoplayercastdemo.ui.navigation.AppNavigation
import t.saito.exoplayercastdemo.ui.theme.ExoPlayerCastDemoTheme
import t.saito.exoplayercastdemo.viewmodel.MediaViewModel
import t.saito.exoplayercastdemo.viewmodel.PlayerViewModel

class MainActivity : FragmentActivity() {
    private lateinit var mediaViewModel: MediaViewModel
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var serviceConnection: PlaybackServiceConnection

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        permissions.entries.forEach {
            // Log permission results if needed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions
        requestPermissions()

        // Initialize ViewModels
        mediaViewModel = ViewModelProvider(this)[MediaViewModel::class.java]
        playerViewModel = ViewModelProvider(this)[PlayerViewModel::class.java]

        // Initialize Service Connection
        serviceConnection = PlaybackServiceConnection(this)
        serviceConnection.bind()

        setContent {
            ExoPlayerCastDemoTheme {
                AppNavigation(
                    mediaViewModel = mediaViewModel,
                    playerViewModel = playerViewModel,
                    serviceConnection = serviceConnection,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroy() {
        serviceConnection.unbind()
        super.onDestroy()
    }
}