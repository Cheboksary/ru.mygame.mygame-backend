package ru.mygame.models

import ru.mygame.Connection
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashSet

class Lobby(_gameId: String? = null) {
    val gameId: String = _gameId ?: AtomicInteger(0).toString()
    private val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())

    fun addConnection(connection: Connection){
        connections += connection
    }
}