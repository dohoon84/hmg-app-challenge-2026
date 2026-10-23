package io.hmg.appchallenge.foundation.vehicle

/**
 * Generation token for async capability callbacks.
 * A callback that arrives after [close] must not register a new listener.
 * [track] after close runs the unregister action immediately.
 */
class ListenerSession {
    private val lock = Any()
    private var token = 0
    private var next = 0
    private val cleanups = mutableListOf<() -> Unit>()

    fun open(): Int = synchronized(lock) {
        next += 1
        token = next
        token
    }

    fun isCurrent(candidate: Int): Boolean = synchronized(lock) {
        candidate != 0 && candidate == token
    }

    /**
     * Keeps [cleanup] until [close] when [candidate] is the open token.
     * Otherwise runs [cleanup] now and returns false.
     */
    fun track(candidate: Int, cleanup: () -> Unit): Boolean {
        val owned = synchronized(lock) {
            if (candidate == 0 || candidate != token) {
                false
            } else {
                cleanups += cleanup
                true
            }
        }
        if (!owned) {
            runCatching { cleanup() }
        }
        return owned
    }

    fun close() {
        val actions = synchronized(lock) {
            token = 0
            cleanups.toList().also { cleanups.clear() }
        }
        actions.forEach { action ->
            runCatching { action() }
        }
    }
}
