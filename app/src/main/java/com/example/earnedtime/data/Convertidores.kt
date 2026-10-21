package com.example.earnedtime.data

import androidx.room.TypeConverter

class Convertidores {
    @TypeConverter
    fun listaAString(l: List<Int>): String = l.joinToString(",")

    @TypeConverter
    fun stringALista(s: String): List<Int> =
        if (s.isBlank()) emptyList() else s.split(",").map { it.toInt() }
}