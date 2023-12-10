package ru.mygame.models

import kotlinx.serialization.Serializable

enum class IncomingMsgType{
    GAME_START,
    EARLY_ANSWER,
    REFRESH_QUESTION,
    READY,
    NOT_READY,
    ANSWER,
    VOTE_KICK
}
@Serializable
data class WSIncomingMessage(
    val type: IncomingMsgType,
    val msg: String = ""
)

enum class OutgoingMsgType {
    GAME_START,
    EARLY_ANSWER,
    READY,
    NOT_READY,
    NEXT_ROUND,
    QUESTION,
    ANSWER_ACCEPTED,
    GAME_OVER,
    GAME_OVER_BY_VOTE
}
@Serializable
data class WSOutgoingMessage(
    val type: OutgoingMsgType,
    //todo
)
// todo: @Serializable data class VoteMessage()