package ru.mygame

import io.ktor.websocket.*
import ru.mygame.gameplay.GamePlay
import ru.mygame.models.Player
import ru.mygame.models.PlayerState
import ru.mygame.utilities.Constants
import ru.mygame.utilities.InternalExceptions
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

val lobbies = Collections.synchronizedSet<Connection?>(LinkedHashSet())

class Connection(val session: DefaultWebSocketSession, lobbyId: String? = null) : GamePlay {
    companion object {
        private val uniqueId = AtomicInteger(0)
        fun getLobby(session: DefaultWebSocketSession, lobbyId: String?): Connection {
            if (lobbyId == null) {
                //creating new lobby
                val lobby = Connection(session)
                lobbies += lobby
                return lobby
            }
            //finding an existing lobby
            val lobby = lobbies.find { it.lobbyId == lobbyId }
            if (lobby != null && lobby.gameIsStarted)
                throw InternalExceptions.ConnectingToStartedGameException()
            return lobby ?: throw InternalExceptions.UnavailableLobbyIdException()
        }
    }

    val lobbyId = lobbyId ?: uniqueId.getAndIncrement().toString()
    private val players = Collections.synchronizedSet<Player>(LinkedHashSet())
    var gameIsStarted = false
    var round = 0

    fun deleteLobby() {
        lobbies -= this
    }

    override fun addPlayer(name: String, session: DefaultWebSocketSession): Player {
        if (players.size >= Constants.MAX_PLAYERS)
            throw InternalExceptions.LobbyIsFullException()
        val id: Int =
            if (players.size > 0) players.last().id + 1 else 0 // the player ID will always be 1 greater than previous player had and will be unique
        val player = Player(name, id, session)
        if (players.isEmpty())
            player.state = PlayerState.LEADER
        players += player
        return player // returning added player data class instance
    }

    override fun deletePlayer(player: Player): MutableSet<Player>? {
        return if (players.remove(player))
            players // returning updated set of players
        else null // throw InternalExceptions.PlayerNotFoundException() can be used if needed
    }
    fun deletePlayer(session: DefaultWebSocketSession): MutableSet<Player>? {
        return players.find { it.session == session }?.let { deletePlayer(it) }
    }

    override fun setLiar(): Player {
        val liar = players.elementAt((1..<players.size).random())
        liar.state = PlayerState.LIAR
        return liar
    }

    override fun getLiar(): Player? {
        return players.find { it.state == PlayerState.LIAR }
    }

    override fun getListOfPlayers(): MutableSet<Player> {
        return players
    }

    override fun startTheGame() {
        gameIsStarted = true
        round = 1
    }

    override fun finishAndGetResult(): List<MutableSet<Player>> {
        val result = List(Constants.NUMBER_OF_ROUNDS.toInt()) { mutableSetOf<Player>() }
        var i = 1
        while (i <= Constants.NUMBER_OF_ROUNDS) {
            for (player in players)
                result[i-1].add(Player(player, i))
            //todo compute score here
            i++
        }
        return result
    }

    override fun setAnswer() {
        TODO("Not yet implemented")
    }
}