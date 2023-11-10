package ru.mygame.utilities

import ru.mygame.models.Player
import ru.mygame.models.PlayerState

fun MutableSet<Player>?.getLeader(): Player? {
    return if (this != null && this.first().state == PlayerState.LEADER ){
        this.first()
    }
    else null
}