package com.krazylabs.miri.mvp

/**
 * Process-wide queue from the debug receiver to [FilmingController].
 * Commands sent before the activity creates the controller wait here.
 */
object FilmingBus {
    private val lock = Any()
    private var sink: ((FilmingCommand) -> Unit)? = null
    private val pending = ArrayDeque<FilmingCommand>()

    fun dispatch(command: FilmingCommand) {
        if (command is FilmingCommand.Ignore) return
        val target = synchronized(lock) {
            val current = sink
            if (current == null) pending.addLast(command)
            current
        }
        target?.invoke(command)
    }

    fun attach(target: (FilmingCommand) -> Unit) {
        val queued = synchronized(lock) {
            sink = target
            pending.toList().also { pending.clear() }
        }
        queued.forEach(target)
    }
}
