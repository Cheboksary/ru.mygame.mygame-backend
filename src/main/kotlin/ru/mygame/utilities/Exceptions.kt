package ru.mygame.utilities


object InternalExceptions {
    class UnavailableLobbyIdException : Exception()
    class PlayerNameExpectedException : Exception()
    class LobbyIsFullException: Exception()
    class PlayerNotFoundException: Exception()
    class ConnectingToStartedGameException: Exception()
}