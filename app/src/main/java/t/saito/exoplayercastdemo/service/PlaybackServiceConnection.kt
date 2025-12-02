package t.saito.exoplayercastdemo.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackServiceConnection(private val context: Context) {
    private val _service = MutableStateFlow<PlaybackService?>(null)
    val service: StateFlow<PlaybackService?> = _service.asStateFlow()

    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as PlaybackService.PlaybackServiceBinder
            _service.value = serviceBinder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            isBound = false
        }
    }

    fun bind() {
        if (!isBound) {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbind() {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
            _service.value = null
        }
    }
}
