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

class Connection(lobbyId: String? = null) : GamePlay {
    companion object {
        private val uniqueId = AtomicInteger(0)
        fun getLobby(lobbyId: String?): Connection {
            if (lobbyId == null) {
                //creating new lobby
                val lobby = Connection()
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

    private val lobbyId = lobbyId ?: uniqueId.getAndIncrement().toString()
    private val players = Collections.synchronizedSet<Player>(LinkedHashSet())

    fun getLobbyId() = lobbyId

    fun getLeader(): Player = players.first()

    fun markPlayerReady(session: DefaultWebSocketSession) {
        players.find { it.session == session }?.isReady = true
    }

    fun markPlayerNotReady(session: DefaultWebSocketSession) {
        players.find { it.session == session }?.isReady = false
    }

    fun isEveryoneReady(): Boolean {
        return players.find { !it.isReady } == null
    }

    private var gameIsStarted = false
    private var round = 0

    fun isGameStarted() = gameIsStarted

    fun isEveryoneAnswered(): Boolean {
        for (player in players) {
            if (player.answers.size <= round)
                return false
        }
        return true
    }

    fun nextRound(): Boolean {
        if (round == Constants.NUMBER_OF_ROUNDS)
            return false
        round++
        return true
    }

    fun deleteLobby() {
        lobbies -= this
    }

    override fun addPlayer(name: String, session: DefaultWebSocketSession): Player {
        if (players.size >= Constants.MAX_PLAYERS)
            throw InternalExceptions.LobbyIsFullException()
        val id: Int =
            if (players.size > 0) players.last().id + 1 else 0 // the player ID will always be 1 greater than previous player had and will be unique
        val player = Player(name, id, session)
        if (players.isEmpty()) {
            player.state = PlayerState.LEADER
            player.isReady = true
        }
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

    private fun unSetLiar() {
        getLiar()?.state = PlayerState.PLAYER
    }

    override fun getListOfPlayers(): MutableSet<Player> {
        return players
    }

    override fun startTheGame(): Boolean {
        if (players.size < Constants.MIN_PLAYERS)
            throw InternalExceptions.NotEnoughPlayers()
        gameIsStarted = true
        round = 1
        setLiar()
        return true
    }

    override fun finishAndGetResult(): List<MutableSet<Player>> {
        val result = List(Constants.NUMBER_OF_ROUNDS) { mutableSetOf<Player>() }
        var i = 0
        while (i < Constants.NUMBER_OF_ROUNDS) {
            val roundAnswers = mutableSetOf<String>()
            var liarAnswer = ""
            var correctAnswer = ""
            for (player in players) {
                when (player.state) {
                    PlayerState.LEADER -> correctAnswer = player.answers[i]
                    PlayerState.LIAR -> liarAnswer = player.answers[i]
                    else -> roundAnswers.add(player.answers[i])
                }
            }
            if (roundAnswers.size == 1)
                when (roundAnswers.toString()) {
                    correctAnswer -> for (player in players)
                        if (player.state != PlayerState.LIAR)
                            player.points++
                    liarAnswer -> this.getLiar()!!.points++
                }
            for (player in players)
                result[i].add(Player(player, i))
            i++
        }
        gameIsStarted = false
        round = 0
        unSetLiar()
        return result // returning a table: in a row list of players where each player with only one answer, amount of rows is a NUMBER_OF_ROUNDS, final score is in a last row
    }

    override fun setAnswer(player: Player, answer: String): Boolean {
        if (player.answers.size == round) return false
        players.find { it.session == player.session }!!.answers.add(answer.lowercase().trimMargin())
        return true
    }
}