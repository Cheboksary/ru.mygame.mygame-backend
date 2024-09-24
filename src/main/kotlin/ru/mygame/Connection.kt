package ru.mygame

import io.ktor.websocket.*
import ru.mygame.gameplay.GamePlay
import ru.mygame.models.Player
import ru.mygame.models.PlayerState
import ru.mygame.utilities.Constants
import ru.mygame.utilities.InternalExceptions
import ru.mygame.utilities.sort
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

    var isReadyToDelete = false
        private set

    fun readyToDeleteLobby() {
        isReadyToDelete = true
    }

    fun deleteLobby() {
        lobbies -= this
    }

    fun playerBySession(session: DefaultWebSocketSession): Player = players.first { it.session == session }

    fun getLobbyId() = lobbyId

    fun getLeader(): Player = players.first()

    fun markPlayerReady(player: Player) {
        players.remove(player)
        player.isReady = true
        players.add(player)
        players.sort()
    }

    fun markPlayerNotReady(player: Player) {
        players.remove(player)
        player.isReady = false
        players.add(player)
        players.sort()
    }

    fun isEveryoneReady(): Boolean {
        return players.find { !it.isReady } == null
    }

    private var gameIsStarted = false
    private var round = 0
    private var leaderIsDone = false
    var fastResultLiarPoints = 0
    var fastResultPlayersPoints = 0

    fun isGameStarted() = gameIsStarted

    fun isLeaderDone() = leaderIsDone

    fun markLeaderIsDone() {
        leaderIsDone = true
    }

    fun isEveryoneAnswered(): Boolean {
        for (player in players) {
            if (player.answers.size < round)
                return false
        }
        return true
    }

    fun nextRound(): Boolean {
        if (round == Constants.NUMBER_OF_ROUNDS)
            return false
        round++
        leaderIsDone = false
        return true
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
        else
            null // throw InternalExceptions.PlayerNotFoundException() can be used if needed
    }

    fun deletePlayer(session: DefaultWebSocketSession): MutableSet<Player>? {
        return players.find { it.session == session }?.let { deletePlayer(it) }
    }

    override fun setLiar(): Player {
        val liar = players.elementAt((1..<players.size).random())
        players.remove(liar)
        liar.state = PlayerState.LIAR
        players.add(liar)
        players.sort()
        return liar
    }

    override fun getLiar(): Player? {
        return players.find { it.state == PlayerState.LIAR }
    }

    private fun unSetLiar() {
        val liar = getLiar() ?: return
        players.remove(liar)
        liar.state = PlayerState.PLAYER
        players.add(liar)
        players.sort()
    }

    override fun getListOfPlayers(): MutableSet<Player> {
        return players
    }

    override fun startTheGame(): Boolean {
        if (players.size < Constants.MIN_PLAYERS)
            throw InternalExceptions.NotEnoughPlayers()
        gameIsStarted = true
        round = 1
        unSetLiar()
        setLiar()
        return true
    }

    override fun finishAndGetResult(): List<MutableSet<Player>> {
        val result = List(Constants.NUMBER_OF_ROUNDS) { mutableSetOf<Player>() }
        var i = 0
        val playerList = players.map { it.copy() }
        while (i < Constants.NUMBER_OF_ROUNDS) {
            val roundAnswers = mutableSetOf<String>()
            var liarAnswer = ""
            var correctAnswer = ""
            for (player in playerList) {
                when (player.state) {
                    PlayerState.LEADER -> correctAnswer = player.answers[i]
                    PlayerState.LIAR -> liarAnswer = player.answers[i]
                    else -> roundAnswers.add(player.answers[i])
                }
            }
            if (roundAnswers.size == 1)
                when (roundAnswers.first()) {
                    correctAnswer -> {
                        for (player in playerList)
                            if (player.state != PlayerState.LIAR)
                                player.points++
                        fastResultPlayersPoints++
                    }
                    liarAnswer -> {
                        playerList.find { it.state == PlayerState.LIAR }!!.points++
                        fastResultLiarPoints++
                    }
                }
            for (player in playerList)
                result[i].add(Player(player, i))
            i++
        }
        val clearedPlayerList = players.map { it.copy(isReady = it.state == PlayerState.LEADER, answers = mutableListOf(),points = 0) }
        players.clear()
        players.addAll(clearedPlayerList)
        gameIsStarted = false
        round = 0
        leaderIsDone = false
        return result // returning a table: in a row list of players where each player with only one answer, amount of rows is a NUMBER_OF_ROUNDS, final score is in a last row
    }

    override fun setAnswer(player: Player, answer: String): Boolean {
        if (player.answers.size == round) return false
        players.remove(player)
        player.answers.add(answer.lowercase().trimMargin().trim(' ','.',',','!','&','?','*','%','^',':',';','~','$','-','=','+','/','|'))
        players.add(player)
        players.sort()
        return true
    }

    fun replaceAnswer(player: Player, answer: String): Boolean {
        players.remove(player)
        player.answers.removeLast()
        player.answers.add(answer.lowercase().trimMargin().trim(' ','.',',','!','&','?','*','%','^',':',';','~','$','-','=','+','/','|'))
        players.add(player)
        players.sort()
        return true
    }
}