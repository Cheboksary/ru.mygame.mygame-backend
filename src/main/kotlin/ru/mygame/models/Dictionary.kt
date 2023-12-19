package ru.mygame.models

import java.io.File

object Dictionary {
    private const val FILE_PATH = "src/main/kotlin/ru/mygame/models/dictionary.txt"
    private val dictionary = File(FILE_PATH).readLines()
    fun getSomeString(): String {
        return dictionary.random()
    }
}