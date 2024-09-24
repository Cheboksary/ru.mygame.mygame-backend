package ru.mygame.plugins

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import ru.mygame.Connection
import ru.mygame.models.*
import ru.mygame.models.IncomingMsgType.*
import ru.mygame.utilities.*
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
        webSocket("/echo") {
            send("Hello from WebSocket server")
            for (frame in incoming) {
                frame as? Frame.Text ?: continue
                val receivedText = frame.readText()
                send("Echo: $receivedText")
            }
        }
        webSocket("/") {
            var thisLobby: Connection? = null
            try {
                val connectionId = call.parameters["lobby_ID"]
                val playerName = call.parameters["name"] ?: throw InternalExceptions.PlayerNameExpectedException()

                thisLobby = Connection.getLobby(connectionId)
                thisLobby.addPlayer(playerName, this)

                thisLobby.getListOfPlayers().sendAllSerialized(
                    WSOutgoingMessage(
                        type = OutgoingMsgType.LOBBY_STATE,
                        lobbyId = thisLobby.getLobbyId(),
                        playersList = thisLobby.getListOfPlayers().toList()
                    )
                )

                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val incomingMessage: WSIncomingMessage
                    try {
                        println(frame.data)
                        println("//===///===/=/=/=")
                        println(frame.readText())
                        incomingMessage = Json.decodeFromString<WSIncomingMessage>(frame.readText())
                    } catch (e: IllegalArgumentException) {
                        println("Decode message problem: $e")
                        continue
                    }
                    when (incomingMessage.type) {
                        GAME_START -> {
                            val thisPlayer = thisLobby.playerBySession(this)
                            if (thisPlayer.state == PlayerState.LEADER && !thisLobby.isGameStarted() && thisLobby.isEveryoneReady()) {
                                try {
                                    thisLobby.startTheGame()
                                } catch (e: InternalExceptions.NotEnoughPlayers) {
                                    sendSerialized(
                                        WSOutgoingMessage(
                                            type = OutgoingMsgType.EXCEPTION,
                                            msg = "Not enough players"
                                        )
                                    )
                                    continue
                                }
                                thisLobby.getListOfPlayers().sendAllSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.GAME_START
                                    )
                                )
                                (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.YOUR_LIAR
                                    )
                                )
                                val question = thisLobby.getQuestion()
                                thisLobby.setAnswer(thisPlayer,question)
                                sendSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.QUESTION,
                                        msg = question
                                    )
                                )
                                (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.QUESTION,
                                        msg = question
                                    )
                                )
                            }
                        }
                        LEADER_DONE -> {
                            if (thisLobby.playerBySession(this).state == PlayerState.LEADER && thisLobby.isGameStarted()) {
                                thisLobby.markLeaderIsDone()
                                thisLobby.getListOfPlayers().sendAllSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.LEADER_DONE
                                    )
                                )
                            }
                        }
                        REFRESH_QUESTION -> {
                            //todo: add leader is not done check
                            // done
                            val thisPlayer = thisLobby.playerBySession(this)
                            if (thisPlayer.state == PlayerState.LEADER && thisLobby.isGameStarted() && thisPlayer.answers.isNotEmpty() && !thisLobby.isLeaderDone()) {
                                val question = thisLobby.getQuestion()
                                thisLobby.replaceAnswer(thisPlayer, question)
                                sendSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.QUESTION,
                                        msg = question
                                    )
                                )
                                (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.QUESTION,
                                        msg = question
                                    )
                                )
                            }
                        }
                        READY -> {
                            val thisPlayer = thisLobby.playerBySession(this)
                            if (thisPlayer.state != PlayerState.LEADER && !thisPlayer.isReady) {
                                thisLobby.markPlayerReady(thisPlayer)
                                thisLobby.getListOfPlayers().sendAllSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.READY,
                                        player = thisPlayer
                                    )
                                )
                            }
                        }
                        NOT_READY -> {
                            val thisPlayer = thisLobby.playerBySession(this)
                            if (thisPlayer.state != PlayerState.LEADER && thisPlayer.isReady) {
                                thisLobby.markPlayerNotReady(thisPlayer)
                                thisLobby.getListOfPlayers().sendAllSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.NOT_READY,
                                        player = thisPlayer
                                    )
                                )
                            }
                        }
                        ANSWER -> {
                            val thisPlayer = thisLobby.playerBySession(this)
                            if (thisPlayer.state == PlayerState.LEADER || !thisLobby.isLeaderDone()) continue
                            if (thisLobby.setAnswer(thisPlayer, incomingMessage.msg))
                                sendSerialized(
                                    WSOutgoingMessage(
                                        type = OutgoingMsgType.ANSWER_ACCEPTED,
                                        msg = incomingMessage.msg
                                    )
                                )
                            if (thisLobby.isEveryoneAnswered())
                                if (thisLobby.nextRound()) {
                                    thisLobby.getListOfPlayers().sendAllSerialized(
                                        WSOutgoingMessage(
                                            type = OutgoingMsgType.NEXT_ROUND
                                        )
                                    )
                                    val question = thisLobby.getQuestion()
                                    thisLobby.setAnswer(thisLobby.getLeader(),question)
                                    (thisLobby.getLeader().session as WebSocketServerSession).sendSerialized(
                                        WSOutgoingMessage(
                                            type = OutgoingMsgType.QUESTION,
                                            msg = question
                                        )
                                    )
                                    (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(
                                        WSOutgoingMessage(
                                            type = OutgoingMsgType.QUESTION,
                                            msg = question
                                        )
                                    )
                                }
                                else {
                                    val resultTable = thisLobby.finishAndGetResult()
                                    if (thisLobby.fastResultLiarPoints >= thisLobby.fastResultPlayersPoints)
                                        thisLobby.getListOfPlayers().sendAllSerialized(
                                            WSOutgoingMessage(
                                                type = OutgoingMsgType.LIAR_WON,
                                                player = thisLobby.getLiar()
                                            )
                                        )
                                    else
                                        thisLobby.getListOfPlayers().sendAllSerialized(
                                            WSOutgoingMessage(
                                                type = OutgoingMsgType.PLAYERS_WON,
                                                player = thisLobby.getLiar()
                                            )
                                        )
                                    thisLobby.getListOfPlayers().sendAllSerialized(
                                        WSOutgoingMessage(
                                            type = OutgoingMsgType.GAME_OVER,
                                            playersList = thisLobby.getListOfPlayers().toList(),
                                            resultTable = resultTable
                                        )
                                    )
                                    //todo: do unready all players
                                }
                        }
                        VOTE_KICK -> TODO()
                    }
                }
            } catch (e: InternalExceptions.PlayerNameExpectedException) {
                this.close(
                    CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Parameter name expected")
                )
            } catch (e: InternalExceptions.UnavailableLobbyIdException) {
                this.close(
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
                //if (thisLobby?.isGameStarted() == true && thisLobby.deletePlayer(this)?.getLiar() == null)
                //    thisLobby.getListOfPlayers().forEach { it.session!!.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER,"Liar left the game when it was started")) }
                if (thisLobby != null && !thisLobby.isReadyToDelete) {
                    thisLobby.deletePlayer(this)
                    if (thisLobby.getListOfPlayers().getLeader() == null) {
                        thisLobby.readyToDeleteLobby()
                        thisLobby.getListOfPlayers().forEach {
                            it.session!!.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Leader left the game"))
                        }
                        thisLobby.deleteLobby()
                        println("лобби удалено: ведущий покинул игру")
                    } else if (thisLobby.isGameStarted() && thisLobby.getListOfPlayers().getLiar() == null) {
                        thisLobby.readyToDeleteLobby()
                        thisLobby.getListOfPlayers().forEach {
                            it.session!!.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Liar left the game when it was started"))
                        }
                        thisLobby.deleteLobby()
                        println("лобби удалено: врун покинул игру")
                    } else if (thisLobby.getListOfPlayers().isNotEmpty()) {
                        thisLobby.getListOfPlayers().sendAllSerialized(
                            WSOutgoingMessage(
                                type = OutgoingMsgType.LOBBY_STATE,
                                playersList = thisLobby.getListOfPlayers().toList()
                            )
                        )
                    }
                }
            }
        }
    }
}