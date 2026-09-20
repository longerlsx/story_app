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
    val lastPageLayout: ReaderPageLayoutKey? = null,
    val navigationEpoch: Long = 0,
    val inputRevision: Long = 0,
)

/** A request is intent; only the visible layout can confirm durable progress. */
internal class ReaderPositionState(initial: ReadingAnchor) {
    private var nextId = 0L
    private var failedRequest: ReaderPositionRequest? = null
    private var pageLayout: ReaderPageLayoutKey? = null
    var navigationEpoch by mutableStateOf(0L)
        private set
    var inputRevision by mutableStateOf(0L)
        private set
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
        failedRequest = null
        navigationEpoch++
        inputRevision++
        request = ReaderPositionRequest(nextId++, anchor, lastPage = lastPage,
            lastPageLayout = pageLayout.takeIf { lastPage },
            navigationEpoch = navigationEpoch, inputRevision = inputRevision)
    }

    fun turn(direction: Int, layout: ReaderPageLayoutKey, expectedEpoch: Long? = null): ReaderPositionRequest? {
        require(direction == -1 || direction == 1)
        if (expectedEpoch != null && expectedEpoch != navigationEpoch) return null
        error = null
        failedRequest = null
        inputRevision++
        val base = request ?: ReaderPositionRequest(nextId++, confirmedAnchor)
        return base.copy(id = nextId++, turns = base.turns + ReaderPageMove(direction, layout),
            navigationEpoch = navigationEpoch, inputRevision = inputRevision).also { request = it }
    }

    fun reflow() {
        if (error != null) return
        handoff()
    }

    fun notePageLayout(layout: ReaderPageLayoutKey) { pageLayout = layout }

    /** Revoke the old executor without discarding the accepted navigation or retry state. */
    fun handoff() {
        if (error != null) return
        request = (request ?: ReaderPositionRequest(nextId++, confirmedAnchor,
            navigationEpoch = navigationEpoch, inputRevision = inputRevision)).copy(id = nextId++)
    }

    /** Resolving old PAGE input is not acknowledgement of a displayed page. */
    fun resolvedAnchor(id: Long, anchor: ReadingAnchor): Boolean {
        val current = request?.takeIf { it.id == id } ?: return false
        request = current.copy(id = nextId++, anchor = anchor, turns = emptyList(),
            lastPage = false, lastPageLayout = null)
        return true
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
