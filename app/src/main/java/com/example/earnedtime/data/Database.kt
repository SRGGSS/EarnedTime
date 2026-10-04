package com.example.earnedtime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


//https://medium.com/@anandgaur2207/room-database-in-android-d5f279d4648a
@Database(entities = [UsageEntry::class, Limite::class, ObjetivoEntry::class, DomainEntry::class, AppEntry::class], version = 6, exportSchema = false)
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
                ).build()       //No fallbackToDestructiveMigration, debe asegurarse persistencia entre actualizaciones
                INSTANCE = instance
                return instance
            }
        }
    }
}