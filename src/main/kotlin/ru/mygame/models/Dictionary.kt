package ru.mygame.models

object Dictionary {
    private val res = ClassLoader.getSystemResourceAsStream("dictionary.txt")
    private val dictionary = res?.bufferedReader()?.readLines() ?: throw Exception("the dictionary resource could not be found or the resource is in a package that is not opened unconditionally")
    fun getSomeString(): String {
        return dictionary.random()
    }
}