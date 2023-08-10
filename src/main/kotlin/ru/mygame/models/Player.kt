package ru.mygame.models

import kotlinx.serialization.Serializable

enum class PlayerState{
    LEADER,
    LIAR,
    PLAYER
}
@Serializable
data class Player(
    val name: String,
    var state: PlayerState = PlayerState.PLAYER,
    var points: Int = 0,
    val answers: MutableList<String> = mutableListOf()
)
