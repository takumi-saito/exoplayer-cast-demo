package t.saito.exoplayercastdemo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import t.saito.exoplayercastdemo.MainActivity
import t.saito.exoplayercastdemo.R
import t.saito.exoplayercastdemo.server.MediaStreamHandler
import t.saito.exoplayercastdemo.util.Constants
import java.net.BindException

class LocalMediaServerService : Service() {
    companion object {
        private const val TAG = "LocalMediaServerService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverInstance: ApplicationEngine? = null
    private var currentPort: Int = Constants.SERVER_DEFAULT_PORT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            Constants.ACTION_START_SERVER -> {
                if (serverInstance == null) {
                    startForeground(Constants.SERVER_NOTIFICATION_ID, createNotification())
                    startServer()
                } else {
                    Log.d(TAG, "Server already running on port $currentPort")
                    // 既に起動中の場合も成功を通知
                    sendBroadcast(Intent(Constants.ACTION_SERVER_STARTED).apply {
                        putExtra(Constants.KEY_SERVER_PORT, currentPort)
                        setPackage(packageName)
                    })
                }
            }
            Constants.ACTION_STOP_SERVER -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopServer()
        super.onDestroy()
    }

    private fun startServer() {
        serviceScope.launch {
            var port = Constants.SERVER_DEFAULT_PORT
            var started = false

            Log.d(TAG, "startServer: beginning with port $port, max retries=${Constants.SERVER_MAX_PORT_RETRY}")

            repeat(Constants.SERVER_MAX_PORT_RETRY) { attempt ->
                try {
                    Log.d(TAG, "Attempting to start server on port $port (attempt ${attempt + 1})")

                    serverInstance = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                        routing {
                            get("/media/{mediaId}") {
                                val mediaId = call.parameters["mediaId"]
                                if (mediaId != null) {
                                    MediaStreamHandler.handleMediaRequest(call, applicationContext, mediaId)
                                } else {
                                    call.respondText("Missing mediaId", ContentType.Text.Plain, io.ktor.http.HttpStatusCode.BadRequest)
                                }
                            }
                            get("/health") {
                                Log.d(TAG, "Health endpoint called")
                                call.respondText("OK", ContentType.Text.Plain)
                            }
                        }
                    }.start(wait = false)

                    Log.d(TAG, "embeddedServer.start() completed for port $port")
                    currentPort = port

                    // Wait for server to be ready by health check
                    val serverReady = waitForServerReady(port)
                    if (!serverReady) {
                        Log.e(TAG, "Server started but health check failed on port $port")
                        serverInstance?.stop(1000, 2000)
                        serverInstance = null
                        port++
                        return@repeat
                    }

                    started = true
                    Log.i(TAG, "Server started and health check passed on port $port")

                    // 成功をBroadcast
                    saveServerState(true, port)
                    sendBroadcast(Intent(Constants.ACTION_SERVER_STARTED).apply {
                        putExtra(Constants.KEY_SERVER_PORT, port)
                        setPackage(packageName)
                    })

                    return@launch
                } catch (e: BindException) {
                    Log.w(TAG, "Port $port is in use, trying next...")
                    port++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start server on port $port", e)
                    port++
                }
            }

            if (!started) {
                Log.e(TAG, "Failed to start server after ${Constants.SERVER_MAX_PORT_RETRY} attempts")
                saveServerState(false, -1)
                sendBroadcast(Intent(Constants.ACTION_SERVER_START_FAILED).apply {
                    putExtra(Constants.KEY_ERROR_MESSAGE, "Could not find available port")
                    setPackage(packageName)
                })
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        Log.d(TAG, "Stopping server...")
        try {
            serverInstance?.stop(1000, 2000)
            serverInstance = null
            saveServerState(false, -1)
            Log.i(TAG, "Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    private suspend fun waitForServerReady(port: Int): Boolean {
        // サーバー起動を少し待つ（Ktorがソケットをバインドするまで）
        Log.d(TAG, "waitForServerReady: waiting 500ms for server initialization on port $port")
        kotlinx.coroutines.delay(500)

        repeat(15) { attempt ->
            try {
                // Android では localhost より 127.0.0.1 の方が信頼性が高い
                val url = java.net.URL("http://127.0.0.1:$port/health")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 300
                connection.readTimeout = 300
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                Log.d(TAG, "Health check attempt ${attempt + 1}: responseCode=$responseCode")
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    Log.d(TAG, "Health check response: $response")
                    if (response == "OK") {
                        Log.d(TAG, "Health check passed on attempt ${attempt + 1}")
                        connection.disconnect()
                        return true
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.d(TAG, "Health check attempt ${attempt + 1} failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            kotlinx.coroutines.delay(100) // Wait 100ms before retry
        }
        return false
    }

    private fun saveServerState(isRunning: Boolean, port: Int) {
        getSharedPreferences(Constants.SERVER_SHARED_PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(Constants.KEY_IS_RUNNING, isRunning)
            .putInt(Constants.KEY_SERVER_PORT, port)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.SERVER_NOTIFICATION_CHANNEL_ID,
                Constants.SERVER_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Local media server for Cast streaming"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocalMediaServerService::class.java).apply {
            action = Constants.ACTION_STOP_SERVER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.SERVER_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Local Media Server")
            .setContentText("Streaming local media to Cast device")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .build()
    }
}
