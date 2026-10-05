package com.example.earnedtime

import android.Manifest
import android.app.NotificationChannel
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationManager

val CHANNEL_ID: String = "com.chikeandroid.tutsplustalerts.ANDROID"

class NotificationManager {

    var Iniciado = false;

    fun PonerNotificacion(context: Context, dominio: String, tiempo: Int) {

        //https://developer.android.com/develop/ui/compose/notifications/create-notification?hl=es-419
        val textTitle = "Uso de  ${dominio}"
        val TiempoString = formatTime(tiempo.toLong())
        val textContent = "Has utilizado ${dominio} durante ${TiempoString.subSequence(0,TiempoString.length-5)}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
             .setSmallIcon(R.drawable.ajustes)
             .setContentTitle(textTitle)
             .setContentText(textContent)
             .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@with
            }

            notify(1, builder.build())  //Prueba con notificationId 1, reemplazará el último
            Iniciado=true
        }
    }

    fun createNotificationChannel(context: Context) {   //Se crea el canal de notificación (ejecutado al inicio)
        // https://developer.android.com/develop/ui/compose/notifications/create-notification?hl=es-419
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Principal"
            val descriptionText = "Notificaciones EarnedTime"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system.
            val notificationManager: NotificationManager =
                context.getSystemService(NotificationManager::class.java) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


}