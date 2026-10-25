package com.krazylabs.miri.mvp

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FilmingController @Inject constructor() : Filming {
    private val _active = MutableStateFlow(false)
    override val active: StateFlow<Boolean> = _active

    private val _signals = MutableStateFlow(0)
    override val signals: StateFlow<Int> = _signals

    private val queue = ArrayDeque<FilmingCommand>()

    init {
        FilmingBus.attach(::apply)
    }

    fun apply(command: FilmingCommand) {
        when (command) {
            is FilmingCommand.Set -> _active.value = command.on
            is FilmingCommand.Play, is FilmingCommand.Stop -> {
                synchronized(queue) { queue.addLast(command) }
                _signals.value = _signals.value + 1
            }
            FilmingCommand.Ignore -> Unit
        }
    }

    override fun drain(): List<FilmingCommand> = synchronized(queue) {
        if (queue.isEmpty()) emptyList() else ArrayList(queue).also { queue.clear() }
    }
}
