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
        webSocket("/") {
            var thisLobby: Connection? = null
            try {
                val connectionId = call.parameters["lobby_ID"]
                val playerName = call.parameters["name"] ?: throw InternalExceptions.PlayerNameExpectedException()
                send("$connectionId || $playerName ") // echo
                thisLobby = Connection.getLobby(connectionId)
                sendSerialized("Connected to lobby id ${thisLobby.getLobbyId()}") // echo
                val thisPlayer = thisLobby.addPlayer(playerName, this)
                thisLobby.getListOfPlayers().sendAllSerialized(thisLobby.getListOfPlayers()) // echo

                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val msg: WSIncomingMessage
                    try {
                        println(frame.data)
                        println("//===///===/=/=/=")
                        println(frame.readText())
                        msg = Json.decodeFromString<WSIncomingMessage>(frame.readText())
                    } catch (e: IllegalArgumentException) {
                        println("Decode message problem: $e")
                        continue
                    }
                    when (msg.type) {
                        GAME_START -> {
                            if (thisPlayer.state == PlayerState.LEADER && !thisLobby.isGameStarted() && thisLobby.isEveryoneReady()) {
                                try {
                                    thisLobby.startTheGame()
                                } catch (e: InternalExceptions.NotEnoughPlayers) {
                                    sendSerialized(WSOutgoingMessage(OutgoingMsgType.EXCEPTION, "Not enough players"))
                                    continue
                                }
                                (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(
                                    WSOutgoingMessage(OutgoingMsgType.YOUR_LIAR)
                                )
                                thisLobby.getListOfPlayers().sendAllSerialized(WSOutgoingMessage(OutgoingMsgType.GAME_START))
                                val question = thisLobby.getQuestion()
                                thisPlayer.answers.add(question)
                                sendSerialized(WSOutgoingMessage(OutgoingMsgType.QUESTION, question))
                                (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(
                                    WSOutgoingMessage(OutgoingMsgType.QUESTION, question)
                                )
                            }
                        }
                        LEADER_DONE -> {
                            if (thisPlayer.state == PlayerState.LEADER && thisLobby.isGameStarted()) {
                                thisLobby.markLeaderIsDone()
                                thisLobby.getListOfPlayers().sendAllSerialized(WSOutgoingMessage(OutgoingMsgType.LEADER_DONE))
                            }
                        }
                        REFRESH_QUESTION -> {
                            //todo: add leader is not done check
                            // done
                            if (thisPlayer.state == PlayerState.LEADER && thisLobby.isGameStarted() && thisPlayer.answers.isNotEmpty() && !thisLobby.isLeaderDone()) {
                                val question = thisLobby.getQuestion()
                                thisPlayer.answers.removeLast()
                                thisPlayer.answers.add(question)
                                sendSerialized(WSOutgoingMessage(OutgoingMsgType.QUESTION, question))
                                (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(
                                    WSOutgoingMessage(OutgoingMsgType.QUESTION, question)
                                )
                            }
                        }
                        READY -> {
                            if (thisPlayer.state != PlayerState.LEADER && !thisPlayer.isReady) {
                                thisLobby.markPlayerReady(this)
                                thisLobby.getListOfPlayers().sendAllSerialized(WSOutgoingMessage(OutgoingMsgType.READY,"",thisPlayer))
                            }
                        }
                        NOT_READY -> {
                            if (thisPlayer.state != PlayerState.LEADER && thisPlayer.isReady) {
                                thisLobby.markPlayerNotReady(this)
                                thisLobby.getListOfPlayers().sendAllSerialized(WSOutgoingMessage(OutgoingMsgType.NOT_READY,"",thisPlayer))
                            }
                        }
                        ANSWER -> {
                            //todo: add leader is done check
                            // done
                            if (thisPlayer.state == PlayerState.LEADER || !thisLobby.isLeaderDone()) continue
                            if (thisLobby.setAnswer(thisPlayer, msg.msg))
                                sendSerialized(WSOutgoingMessage(OutgoingMsgType.ANSWER_ACCEPTED,msg.msg))
                            if (thisLobby.isEveryoneAnswered())
                                if (thisLobby.nextRound()) {
                                    thisLobby.getListOfPlayers().sendAllSerialized(WSOutgoingMessage(OutgoingMsgType.NEXT_ROUND))
                                    val question = thisLobby.getQuestion()
                                    thisLobby.getLeader().answers.add(question)
                                    (thisLobby.getLeader().session as WebSocketServerSession).sendSerialized(WSOutgoingMessage(OutgoingMsgType.QUESTION,question))
                                    (thisLobby.getLiar()!!.session as WebSocketServerSession).sendSerialized(WSOutgoingMessage(OutgoingMsgType.QUESTION,question))
                                }
                                else {
                                    //todo: hold lair's and leader's states until results are sent
                                    // done
                                    //todo: add sending short result who won
                                    val resultTable = thisLobby.finishAndGetResult()
                                    thisLobby.getListOfPlayers().sendAllSerialized(WSOutgoingMessage(OutgoingMsgType.GAME_OVER,"",null, resultTable))
                                    //todo: do unready all players
                                    // done
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
                if (thisLobby?.deletePlayer(this).getLeader() != null)
                    thisLobby?.getListOfPlayers()!!.sendAllSerialized(thisLobby.getListOfPlayers())
                else {
                    thisLobby?.getListOfPlayers()!!.forEach { it.session!!.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Leader left the game")) }
                    thisLobby.deleteLobby()
                }
            }
        }
    }
}