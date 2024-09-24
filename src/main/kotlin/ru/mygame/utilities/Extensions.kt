package ru.mygame.utilities

import io.ktor.server.websocket.*
import ru.mygame.models.Player
import ru.mygame.models.PlayerState

fun MutableSet<Player>?.getLeader(): Player? {
    return if (!this.isNullOrEmpty() && this.first().state == PlayerState.LEADER) {
        this.first()
    } else null
}

fun MutableSet<Player>.getLiar(): Player? {
    return this.find { it.state == PlayerState.LIAR }
}

fun MutableSet<Player>.sort() {
    val sorted = this.toSortedSet( compareBy { it.id })
    this.clear()
    this.addAll(sorted)
}

suspend inline fun <reified T> MutableSet<Player>.sendAllSerialized(data: T) {
    this.forEach { (it.session as WebSocketServerSession).sendSerialized(data) } // cast to WebSocketServerSession is required for serialization
}

