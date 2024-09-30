package ru.mygame.models

import java.io.File

object Dictionary {
    private const val FILE_PATH = "assets/diction.txt"
    private val dictionary = File(FILE_PATH).readLines()
    fun getSomeString(): String {
        return dictionary.random()
    }
}