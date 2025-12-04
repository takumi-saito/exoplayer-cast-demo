package t.saito.exoplayercastdemo

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.IntroductoryOverlay
import com.google.android.gms.cast.framework.SessionManagerListener
import t.saito.exoplayercastdemo.service.PlaybackServiceConnection
import t.saito.exoplayercastdemo.ui.navigation.AppNavigation
import t.saito.exoplayercastdemo.ui.theme.ExoPlayerCastDemoTheme
import t.saito.exoplayercastdemo.viewmodel.MediaViewModel
import t.saito.exoplayercastdemo.viewmodel.PlayerViewModel
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var mediaViewModel: MediaViewModel
    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var serviceConnection: PlaybackServiceConnection

    // Cast関連
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var introductoryOverlay: IntroductoryOverlay? = null
    private var castButtonView: View? = null

    private val castExecutor = Executors.newSingleThreadExecutor()

    // CastStateListener - デバイス検出状態の監視
    private val castStateListener = CastStateListener { newState ->
        Log.d(TAG, "CastStateListener: state changed to $newState")
        when (newState) {
            CastState.NO_DEVICES_AVAILABLE -> {
                Log.d(TAG, "No Cast devices available")
            }
            CastState.NOT_CONNECTED -> {
                Log.d(TAG, "Cast devices available but not connected")
                showIntroductoryOverlay()
            }
            CastState.CONNECTING -> {
                Log.d(TAG, "Connecting to Cast device")
            }
            CastState.CONNECTED -> {
                Log.d(TAG, "Connected to Cast device")
            }
        }
    }

    // SessionManagerListener - セッション状態の詳細管理
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            Log.d(TAG, "Cast session starting")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session started: $sessionId")
            castSession = session
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.e(TAG, "Cast session start failed: $error")
        }

        override fun onSessionEnding(session: CastSession) {
            Log.d(TAG, "Cast session ending")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Cast session ended: $error")
            if (session == castSession) {
                castSession = null
            }
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session resuming: $sessionId")
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.d(TAG, "Cast session resumed, wasSuspended: $wasSuspended")
            castSession = session
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.e(TAG, "Cast session resume failed: $error")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            Log.d(TAG, "Cast session suspended: $reason")
        }
    }

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

        // Initialize CastContext asynchronously
        initializeCastContext()

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
                    onCastButtonCreated = { view -> castButtonView = view },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun initializeCastContext() {
        try {
            val castContextTask = CastContext.getSharedInstance(this, castExecutor)
            castContextTask.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    castContext = task.result
                    Log.d(TAG, "CastContext initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize CastContext", task.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CastContext", e)
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

    override fun onResume() {
        super.onResume()
        // Register Cast listeners
        castContext?.let { context ->
            context.addCastStateListener(castStateListener)
            context.sessionManager.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
        }
    }

    override fun onPause() {
        // Unregister Cast listeners
        castContext?.let { context ->
            context.removeCastStateListener(castStateListener)
            context.sessionManager.removeSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
        }
        super.onPause()
    }

    override fun onDestroy() {
        serviceConnection.unbind()
        castExecutor.shutdown()
        super.onDestroy()
    }

    /**
     * 初回Castデバイス検出時にIntroductoryOverlayを表示
     * UXガイドライン: ユーザーにCast機能を認識させる
     */
    private fun showIntroductoryOverlay() {
        introductoryOverlay?.remove()

        val button = castButtonView as? MediaRouteButton
        if (button != null && button.visibility == View.VISIBLE) {
            runOnUiThread {
                try {
                    introductoryOverlay = IntroductoryOverlay.Builder(this, button)
                        .setTitleText(getString(R.string.introducing_cast))
                        .setSingleTime()
                        .setOnOverlayDismissedListener { introductoryOverlay = null }
                        .build()
                    introductoryOverlay?.show()
                    Log.d(TAG, "IntroductoryOverlay shown")
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing IntroductoryOverlay", e)
                }
            }
        }
    }
}