package ru.mygame.models

import kotlinx.serialization.Serializable

enum class MsgType{
    START,
    READY,
    NOT_READY,
    ANSWER,
}
@Serializable
data class WebSocketMessage(
    val type: MsgType,
    val msg: String = ""
)