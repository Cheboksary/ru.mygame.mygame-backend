package ru.mygame.plugins

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import ru.mygame.Connection
import ru.mygame.models.Player
import ru.mygame.models.PlayerState
import ru.mygame.utilities.Settings
import java.time.Duration
import java.util.Collections

fun Application.configureSockets() {
    install(WebSockets){
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
        webSocket ("/lobby") {
            val connectionId = receiveDeserialized<String?>() // ввод кода комнаты или null при создании
            //connectionId = call.parameters["connectionId"]
            val playerName = receiveDeserialized<String>() // ввод имени игрока
            val player = Player(playerName)
            var thisConnection = connections.find{it.connectionId == connectionId}
            if (connectionId != null && thisConnection == null){ // проверка на существование комнаты с указанным номером
                this.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "unavailable lobby id"))
            }
            if ( thisConnection == null ) { // создание новой комнаты или подключение к существующей
                thisConnection = Connection(this, connectionId)
                player.state = PlayerState.LEADER
                thisConnection.players[this] = player
                connections += thisConnection
            } else
                thisConnection.players[this] = player
            sendSerialized("lobbyId: ${thisConnection.connectionId}")
            thisConnection.players.forEach { (it.key as WebSocketServerSession).sendSerialized(thisConnection.players.values) }

            try {
                for (frame in incoming) {
                    connections.forEach {
                        (it.session as WebSocketServerSession).sendSerialized(player)
                    }
                }
            }
            catch (e:Exception){
                println(e.localizedMessage)
            }
            finally {
                connections -= thisConnection
            }
        }
    }
}