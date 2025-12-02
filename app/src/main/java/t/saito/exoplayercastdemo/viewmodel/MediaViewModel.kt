package t.saito.exoplayercastdemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import t.saito.exoplayercastdemo.data.model.MediaItem
import t.saito.exoplayercastdemo.repository.MediaRepositoryImpl

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepositoryImpl(application)

    private val _mediaList = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaList: StateFlow<List<MediaItem>> = _mediaList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val media = repository.getAllMedia()
                _mediaList.value = media
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load media"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshMedia() {
        loadMedia()
    }
}
