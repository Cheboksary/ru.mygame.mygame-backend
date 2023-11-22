package ru.mygame.utilities

import io.ktor.server.websocket.*
import ru.mygame.models.Player
import ru.mygame.models.PlayerState

fun MutableSet<Player>?.getLeader(): Player? {
    return if (this != null && this.first().state == PlayerState.LEADER) {
        this.first()
    } else null
}

suspend inline fun <reified T> MutableSet<Player>.sendAllSerialized(data: T) {
    this.forEach { (it.session as WebSocketServerSession).sendSerialized(data) } // cast to WebSocketServerSession is required for serialization
}