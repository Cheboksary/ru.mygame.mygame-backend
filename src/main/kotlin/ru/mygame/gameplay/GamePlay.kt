package ru.mygame.gameplay

import io.ktor.websocket.*
import ru.mygame.models.Dictionary
import ru.mygame.models.Player

interface GamePlay {
    fun addPlayer(name: String, session: DefaultWebSocketSession): Player

    fun deletePlayer(player: Player): MutableSet<Player>?

    fun setLiar(): Player

    fun getLiar(): Player?

    fun getListOfPlayers(): MutableSet<Player>

    fun startTheGame(): Boolean

    fun finishAndGetResult(): List<MutableSet<Player>>

    fun getQuestion(): String {
        return Dictionary.getSomeString()
    }

    fun setAnswer(player: Player, answer: String): Boolean
}