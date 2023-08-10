package ru.mygame

import io.ktor.websocket.*
import ru.mygame.models.Player
import java.util.concurrent.atomic.AtomicInteger

class Connection(val session: DefaultWebSocketSession, lobbyId: String? = null) {
    companion object{
        private val uniqueId = AtomicInteger(0)
    }
    val connectionId = lobbyId ?: uniqueId.getAndIncrement().toString()
    val players: MutableMap<DefaultWebSocketSession,Player> = mutableMapOf()
    fun addPlayer(){

    }
}