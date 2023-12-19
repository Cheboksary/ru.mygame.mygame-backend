package ru.mygame.models

import kotlinx.serialization.Serializable

enum class IncomingMsgType {
    GAME_START,
    LEADER_DONE,
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
    EXCEPTION,
    GAME_START,
    YOUR_LIAR,
    QUESTION,
    LEADER_DONE,
    READY,
    NOT_READY,
    ANSWER_ACCEPTED,
    NEXT_ROUND,
    GAME_OVER,
    GAME_OVER_BY_VOTE
}
@Serializable
data class WSOutgoingMessage(
    val type: OutgoingMsgType,
    val msg: String = "",
    val player: Player? = null,
    val resultTable: List<MutableSet<Player>>? = null
)
// todo: @Serializable data class VoteMessage()