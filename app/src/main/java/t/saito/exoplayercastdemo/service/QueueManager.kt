package t.saito.exoplayercastdemo.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import t.saito.exoplayercastdemo.data.model.MediaItem

/**
 * メディアキューを管理するクラス
 * 再生キューの追加、削除、並び替えなどを担当
 */
class QueueManager {

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    // シャッフル用のインデックスリスト
    private var shuffledIndices: List<Int> = emptyList()
    private var shuffledPosition: Int = 0

    /**
     * 現在再生中のアイテムを取得
     */
    fun getCurrentItem(): MediaItem? {
        val index = _currentIndex.value
        val queueList = _queue.value
        return if (index in queueList.indices) queueList[index] else null
    }

    /**
     * キューにアイテムを追加
     */
    fun addToQueue(item: MediaItem) {
        _queue.value = _queue.value + item
        updateShuffledIndices()
    }

    /**
     * キューに複数のアイテムを追加
     */
    fun addAllToQueue(items: List<MediaItem>) {
        _queue.value = _queue.value + items
        updateShuffledIndices()
    }

    /**
     * キューをクリアして新しいアイテムをセット
     */
    fun setQueue(items: List<MediaItem>, startIndex: Int = 0) {
        _queue.value = items
        _currentIndex.value = if (items.isNotEmpty() && startIndex in items.indices) startIndex else -1
        updateShuffledIndices()
        if (_shuffleEnabled.value && items.isNotEmpty()) {
            shuffledPosition = shuffledIndices.indexOf(startIndex)
        }
    }

    /**
     * 特定のインデックスのアイテムを削除
     */
    fun removeAt(index: Int) {
        val queueList = _queue.value.toMutableList()
        if (index in queueList.indices) {
            queueList.removeAt(index)
            _queue.value = queueList

            // 現在のインデックスを調整
            val currentIdx = _currentIndex.value
            if (currentIdx >= queueList.size) {
                _currentIndex.value = queueList.size - 1
            } else if (index < currentIdx) {
                _currentIndex.value = currentIdx - 1
            }
            updateShuffledIndices()
        }
    }

    /**
     * キューをクリア
     */
    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = -1
        shuffledIndices = emptyList()
        shuffledPosition = 0
    }

    /**
     * 次のアイテムに移動
     * @return 次のアイテム、なければnull
     */
    fun skipToNext(): MediaItem? {
        val queueList = _queue.value
        if (queueList.isEmpty()) return null

        val nextIndex = if (_shuffleEnabled.value) {
            getNextShuffledIndex()
        } else {
            getNextSequentialIndex()
        }

        return if (nextIndex != null) {
            _currentIndex.value = nextIndex
            queueList[nextIndex]
        } else {
            null
        }
    }

    /**
     * 前のアイテムに移動
     * @return 前のアイテム、なければnull
     */
    fun skipToPrevious(): MediaItem? {
        val queueList = _queue.value
        if (queueList.isEmpty()) return null

        val prevIndex = if (_shuffleEnabled.value) {
            getPreviousShuffledIndex()
        } else {
            getPreviousSequentialIndex()
        }

        return if (prevIndex != null) {
            _currentIndex.value = prevIndex
            queueList[prevIndex]
        } else {
            null
        }
    }

    /**
     * 特定のインデックスにスキップ
     */
    fun skipToIndex(index: Int): MediaItem? {
        val queueList = _queue.value
        return if (index in queueList.indices) {
            _currentIndex.value = index
            if (_shuffleEnabled.value) {
                shuffledPosition = shuffledIndices.indexOf(index)
            }
            queueList[index]
        } else {
            null
        }
    }

    /**
     * アイテムの位置を移動
     */
    fun moveItem(fromIndex: Int, toIndex: Int) {
        val queueList = _queue.value.toMutableList()
        if (fromIndex in queueList.indices && toIndex in queueList.indices) {
            val item = queueList.removeAt(fromIndex)
            queueList.add(toIndex, item)
            _queue.value = queueList

            // 現在のインデックスを調整
            val currentIdx = _currentIndex.value
            when {
                currentIdx == fromIndex -> _currentIndex.value = toIndex
                fromIndex < currentIdx && toIndex >= currentIdx -> _currentIndex.value = currentIdx - 1
                fromIndex > currentIdx && toIndex <= currentIdx -> _currentIndex.value = currentIdx + 1
            }
            updateShuffledIndices()
        }
    }

    /**
     * リピートモードを設定
     */
    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
    }

    /**
     * リピートモードをトグル
     */
    fun toggleRepeatMode(): RepeatMode {
        val newMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = newMode
        return newMode
    }

    /**
     * シャッフルを設定
     */
    fun setShuffleEnabled(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        if (enabled) {
            updateShuffledIndices()
            shuffledPosition = shuffledIndices.indexOf(_currentIndex.value)
        }
    }

    /**
     * シャッフルをトグル
     */
    fun toggleShuffle(): Boolean {
        val newState = !_shuffleEnabled.value
        setShuffleEnabled(newState)
        return newState
    }

    /**
     * キューが空かどうか
     */
    fun isEmpty(): Boolean = _queue.value.isEmpty()

    /**
     * キューのサイズ
     */
    fun size(): Int = _queue.value.size

    /**
     * 次のアイテムがあるかどうか
     */
    fun hasNext(): Boolean {
        val queueList = _queue.value
        if (queueList.isEmpty()) return false

        return when (_repeatMode.value) {
            RepeatMode.ALL -> true
            RepeatMode.ONE -> true
            RepeatMode.OFF -> {
                if (_shuffleEnabled.value) {
                    shuffledPosition < shuffledIndices.size - 1
                } else {
                    _currentIndex.value < queueList.size - 1
                }
            }
        }
    }

    /**
     * 前のアイテムがあるかどうか
     */
    fun hasPrevious(): Boolean {
        val queueList = _queue.value
        if (queueList.isEmpty()) return false

        return when (_repeatMode.value) {
            RepeatMode.ALL -> true
            RepeatMode.ONE -> true
            RepeatMode.OFF -> {
                if (_shuffleEnabled.value) {
                    shuffledPosition > 0
                } else {
                    _currentIndex.value > 0
                }
            }
        }
    }

    // Private helper methods

    private fun updateShuffledIndices() {
        val size = _queue.value.size
        if (size == 0) {
            shuffledIndices = emptyList()
            return
        }

        val currentIdx = _currentIndex.value
        shuffledIndices = (0 until size).filter { it != currentIdx }.shuffled()

        // 現在のインデックスを先頭に配置
        if (currentIdx in 0 until size) {
            shuffledIndices = listOf(currentIdx) + shuffledIndices
        }
        shuffledPosition = 0
    }

    private fun getNextSequentialIndex(): Int? {
        val queueList = _queue.value
        val currentIdx = _currentIndex.value

        return when (_repeatMode.value) {
            RepeatMode.ONE -> currentIdx
            RepeatMode.ALL -> (currentIdx + 1) % queueList.size
            RepeatMode.OFF -> {
                if (currentIdx < queueList.size - 1) currentIdx + 1 else null
            }
        }
    }

    private fun getPreviousSequentialIndex(): Int? {
        val queueList = _queue.value
        val currentIdx = _currentIndex.value

        return when (_repeatMode.value) {
            RepeatMode.ONE -> currentIdx
            RepeatMode.ALL -> if (currentIdx > 0) currentIdx - 1 else queueList.size - 1
            RepeatMode.OFF -> if (currentIdx > 0) currentIdx - 1 else null
        }
    }

    private fun getNextShuffledIndex(): Int? {
        if (shuffledIndices.isEmpty()) return null

        return when (_repeatMode.value) {
            RepeatMode.ONE -> shuffledIndices[shuffledPosition]
            RepeatMode.ALL -> {
                shuffledPosition = (shuffledPosition + 1) % shuffledIndices.size
                shuffledIndices[shuffledPosition]
            }
            RepeatMode.OFF -> {
                if (shuffledPosition < shuffledIndices.size - 1) {
                    shuffledPosition++
                    shuffledIndices[shuffledPosition]
                } else null
            }
        }
    }

    private fun getPreviousShuffledIndex(): Int? {
        if (shuffledIndices.isEmpty()) return null

        return when (_repeatMode.value) {
            RepeatMode.ONE -> shuffledIndices[shuffledPosition]
            RepeatMode.ALL -> {
                shuffledPosition = if (shuffledPosition > 0) shuffledPosition - 1 else shuffledIndices.size - 1
                shuffledIndices[shuffledPosition]
            }
            RepeatMode.OFF -> {
                if (shuffledPosition > 0) {
                    shuffledPosition--
                    shuffledIndices[shuffledPosition]
                } else null
            }
        }
    }

    enum class RepeatMode {
        OFF,    // リピートなし
        ALL,    // 全曲リピート
        ONE     // 1曲リピート
    }
}
