package com.longerlsx.storyapp.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.longerlsx.storyapp.core.model.ReadingAnchor

/** Only inputs that change line wrapping or page boundaries. */
internal data class ReaderPageLayoutKey(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fontScale: Float,
    val fontSizeSp: Int,
    val lineHeightMultiplier: Float,
    val paragraphSpacingPx: Float,
)

internal data class ReaderPageMove(val direction: Int, val layout: ReaderPageLayoutKey)

internal data class ReaderPositionRequest(
    val id: Long,
    val anchor: ReadingAnchor,
    val turns: List<ReaderPageMove> = emptyList(),
    val lastPage: Boolean = false,
)

/** A request is intent; only the visible layout can confirm durable progress. */
internal class ReaderPositionState(initial: ReadingAnchor) {
    private var nextId = 0L
    private var failedRequest: ReaderPositionRequest? = null
    var confirmedAnchor by mutableStateOf(initial)
        private set
    var hasConfirmedLayout by mutableStateOf(false)
        private set
    var request by mutableStateOf<ReaderPositionRequest?>(ReaderPositionRequest(nextId++, initial))
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun jump(anchor: ReadingAnchor, lastPage: Boolean = false) {
        error = null
        request = ReaderPositionRequest(nextId++, anchor, lastPage = lastPage)
    }

    fun turn(direction: Int, layout: ReaderPageLayoutKey) {
        require(direction == -1 || direction == 1)
        error = null
        val base = request ?: ReaderPositionRequest(nextId++, confirmedAnchor)
        request = base.copy(id = nextId++, turns = base.turns + ReaderPageMove(direction, layout))
    }

    fun reflow() {
        error = null
        request = (request ?: ReaderPositionRequest(nextId++, confirmedAnchor)).copy(id = nextId++)
    }

    fun confirm(id: Long, anchor: ReadingAnchor): Boolean {
        if (request?.id != id) return false
        confirmedAnchor = anchor
        hasConfirmedLayout = true
        request = null
        error = null
        failedRequest = null
        return true
    }

    fun recordViewport(anchor: ReadingAnchor): Boolean {
        if (request != null || error != null || !hasConfirmedLayout) return false
        confirmedAnchor = anchor
        return true
    }

    fun fail(id: Long, message: String) {
        val current = request?.takeIf { it.id == id } ?: return
        failedRequest = current
        request = null
        error = message
    }

    fun retry() {
        error = null
        request = (failedRequest ?: ReaderPositionRequest(nextId++, confirmedAnchor)).copy(id = nextId++)
    }
}
