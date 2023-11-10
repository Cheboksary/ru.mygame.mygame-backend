package ru.mygame.plugins

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import ru.mygame.Connection
import ru.mygame.models.PlayerState
import ru.mygame.utilities.Constants
import ru.mygame.utilities.InternalExceptions
import ru.mygame.utilities.getLeader
import java.time.Duration

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    routing {
        webSocket("/") {
            var thisLobby: Connection? = null
            try {
                val connectionId = call.parameters["lobby_ID"]
                val playerName = call.parameters["name"] ?: throw InternalExceptions.PlayerNameExpectedException()
                send("$connectionId || $playerName ") // echo
                thisLobby = Connection.getLobby(connectionId)
                sendSerialized("Connected to lobby id ${thisLobby.lobbyId}") // echo
                val thisPlayer = thisLobby.addPlayer(playerName, this)
                thisLobby.getListOfPlayers() // echo
                    .forEach { (it.session as WebSocketServerSession).sendSerialized(thisLobby.getListOfPlayers()) } // cast to WebSocketServerSession is required for serialization

                if (thisPlayer.state == PlayerState.LEADER) {                         ////
                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        if (frame.toString() == "start") {     ////
                            thisLobby.gameIsStarted = true
                            thisLobby.getListOfPlayers()
                                .forEach { (it.session as WebSocketServerSession).sendSerialized("Started") }
                            break
                        }
                    }
                } else
                    while(!thisLobby.gameIsStarted) {                                 ////     sketches
                        continue
                    }

                while (thisLobby.round <= Constants.NUMBER_OF_ROUNDS) {
                    sendSerialized(thisLobby.round)
                    if (thisPlayer.state == PlayerState.LEADER) { // LEADER
                        thisLobby.round++
                        val question = thisLobby.getQuestion()
                        thisPlayer.answers.add(question)
                        sendSerialized(question)
                    }
                    if (thisPlayer.state == PlayerState.LIAR) // LIAR
                        for (frame in incoming){
                            if (thisLobby.getListOfPlayers().first().answers[thisLobby.round-1].isNotEmpty()) {
                                sendSerialized(thisLobby.getListOfPlayers().first().answers[thisLobby.round - 1])
                                break
                            }
                        }

                    for (frame in incoming) { // PLAYER , LEADER , LIAR
                        frame as? Frame.Text ?: continue
                        if (thisLobby.getListOfPlayers().all { it.answers.size == thisLobby.round })
                            break
                        if (thisPlayer.state == PlayerState.LEADER)
                            continue
                        if (thisPlayer.answers.size == thisLobby.round)
                            continue
                        thisPlayer.answers.add(frame.toString())
                    }
                }
                thisLobby.getListOfPlayers()
                    .forEach { (it.session as WebSocketServerSession).sendSerialized(thisLobby.getListOfPlayers()) }
                thisLobby.gameIsStarted = false
                thisLobby.round = 0
                                                                                       //// sketches

            } catch (e: InternalExceptions.PlayerNameExpectedException) {
                close(
                    CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Parameter name expected")
                )
            } catch (e: InternalExceptions.UnavailableLobbyIdException) {
                close(
                    CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Unavailable lobby ID")
                )
            } catch (e: InternalExceptions.ConnectingToStartedGameException) {
                close(
                    CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "The game is already started")
                )
            } catch (e: InternalExceptions.LobbyIsFullException) {
                close(
                    CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Lobby is full")
                )
            } finally {
                if (thisLobby?.deletePlayer(this).getLeader() != null)
                    thisLobby?.getListOfPlayers()!!
                        .forEach { (it.session as WebSocketServerSession).sendSerialized(thisLobby.getListOfPlayers()) }
                else {
                    thisLobby?.getListOfPlayers()!!.forEach { it.session!!.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Leader left the game")) }
                    thisLobby.deleteLobby()
                }
            }
        }
    }
}