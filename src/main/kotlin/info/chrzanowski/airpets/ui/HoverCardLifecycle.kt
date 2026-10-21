package info.chrzanowski.airpets.ui

import javax.swing.Timer

internal fun scheduleHover(delay: Int, action: () -> Unit): () -> Unit {
    val timer = Timer(delay) { action() }.apply { isRepeats = false; start() }
    return { timer.stop() }
}

internal class HoverCardLifecycle(
    private val show: (String) -> Unit,
    private val close: () -> Unit,
    private val later: (Int, () -> Unit) -> (() -> Unit) = ::scheduleHover,
) {
    var sessionId: String? = null
        private set
    var visible: Boolean = false
        private set
    private var overPet: String? = null
    private var overCard = false
    private var cancelShow: (() -> Unit)? = null
    private var cancelClose: (() -> Unit)? = null

    fun hover(id: String?) {
        overPet = id
        if (id == null) {
            if (!visible) cancel() else scheduleClose()
        } else if (id == sessionId) {
            cancelClose?.invoke()
            cancelClose = null
        } else {
            cancel()
            overPet = id
            sessionId = id
            cancelShow = later(450) {
                cancelShow = null
                if (sessionId == id && overPet == id) {
                    visible = true
                    show(id)
                }
            }
        }
    }

    fun enterCard() {
        overCard = true
        cancelClose?.invoke()
        cancelClose = null
    }

    fun leaveCard() {
        overCard = false
        scheduleClose()
    }

    private fun scheduleClose() {
        if (overPet != null || overCard || cancelClose != null) return
        cancelClose = later(250) { cancel() }
    }

    fun cancel() {
        cancelShow?.invoke()
        cancelClose?.invoke()
        cancelShow = null
        cancelClose = null
        sessionId = null
        overPet = null
        overCard = false
        visible = false
        close()
    }
}