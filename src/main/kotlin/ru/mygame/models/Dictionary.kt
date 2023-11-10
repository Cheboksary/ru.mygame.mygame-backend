package ru.mygame.models

import java.io.File

object Dictionary {
    private const val filePath = "src/main/kotlin/ru/mygame/models/dictionary.txt"
    private val dictionary = File(filePath).readLines()
    fun getSomeString(): String {
        return dictionary.random()
    }
}