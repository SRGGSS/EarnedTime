package com.example.earnedtime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


//https://medium.com/@anandgaur2207/room-database-in-android-d5f279d4648a
@Database(entities = [UsageEntry::class, ObjetivoEntry::class, DomainEntry::class, AppEntry::class, GrupoEntry::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mapDAO(): mapDAO

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null   //Instancia compartida de base de datos
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "earned_database"
                ).fallbackToDestructiveMigration().build()       //Para el futuro: quitar fallbackToDestructiveMigration() y hacer que se mantengan los datos, incluso al modificar su estrucutra
                INSTANCE = instance
                return instance
            }
        }
    }
}