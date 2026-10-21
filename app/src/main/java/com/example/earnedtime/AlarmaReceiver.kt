package com.example.earnedtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val titulo = intent.getStringExtra("titulo")
        val texto = intent.getStringExtra("texto")
        if(titulo!=null && texto!=null)
        {
            NotificationManager.PonerNotificacion(context, titulo , texto)
        }
    }
}