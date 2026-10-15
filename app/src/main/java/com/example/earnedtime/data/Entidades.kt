package com.example.earnedtime.data

import androidx.room.Embedded
import androidx.room.Entity


@Entity(tableName = "usage_entries", primaryKeys = ["domain", "date"])
data class UsageEntry(
    val domain: String,
    val date: String,
    val secondsUsed: Long = 0L
)

@Entity(tableName = "dominio_entries", primaryKeys = ["domain"])
data class DomainEntry(
    val domain: String,
    val banned: Boolean = false,
    var grupo: Int = 1     //id del grupo al que pertenece
)

data class Limite(
    var LimiteActualSegs: Long = 30L * 60,
    var LimiteBaseSegs: Long = 30L * 60,
    val date: String
)

@Entity(tableName = "objective_entries", primaryKeys = ["id"])
data class ObjetivoEntry(
    val id: String,
    val Objetivo: String,
    val TiempoExtra: Long,
    val LimiteDiario: Int,
    val LimiteSemanal: Int,
    val usoDiario: Int,
    val usoSemanal: Int,
    val dateCompletado: String,
    val fechaLimite: String = "",
    var grupo: Int
)

@Entity(tableName = "app_entries", primaryKeys = ["packageName"])
data class AppEntry(
    val packageName: String,
    val appLabel: String,
    val banned: Boolean = false,
    var grupo: Int     //id del grupo al que pertenece
)

@Entity(tableName = "grupo_entries", primaryKeys = ["id"])
data class GrupoEntry(
    val id: Int,
    val nombre: String,
    @Embedded(prefix = "limite_") var limite: Limite,   //Aplana la tabla
)

