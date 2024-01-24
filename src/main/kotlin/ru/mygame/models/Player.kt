package ru.mygame.models

import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


enum class PlayerState {
    LEADER,
    LIAR,
    PLAYER
}

@Serializable
data class Player(
    val name: String,
    var id: Int,
    var state: PlayerState,
    var points: Int = 0,
    val answers: MutableList<String> = mutableListOf(),
    @Transient
    val session: DefaultWebSocketSession? = null,
    var isReady: Boolean = false
) {
    constructor(name: String, id: Int, session: DefaultWebSocketSession) : this( // primary constructor
        name = name,
        id = id,
        state = PlayerState.PLAYER,
        answers = mutableListOf(),
        session = session
    )
    constructor(player: Player, numberOfAnswer: Int) : this( // auxiliary constructor for players to get models with one [i] answer
        name = player.name,
        id = player.id,
        state = player.state,
        points = player.points,
        answers = mutableListOf(player.answers[numberOfAnswer]),
        isReady = false
    )
}
