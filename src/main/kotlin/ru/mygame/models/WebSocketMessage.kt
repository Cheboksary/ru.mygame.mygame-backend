package ru.mygame.models

import kotlinx.serialization.Serializable

enum class MsgType{
    ANSWER,
    LOG
}
@Serializable
data class WebSocketMessage(
    val type: MsgType,
    val msg: String
)