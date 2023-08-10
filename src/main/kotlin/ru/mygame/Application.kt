package ru.mygame

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import ru.mygame.plugins.*

fun main() {

    //Database.connect("jdbc:postgresql://localhost:5432/mygame","org.postgresql.Driver","postgres","hfrhfr")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureSockets()
    configureRouting()
}