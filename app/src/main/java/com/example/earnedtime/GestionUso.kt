package com.example.earnedtime

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.earnedtime.data.AppDatabase
import com.example.earnedtime.data.AppEntry
import com.example.earnedtime.data.DomainEntry
import com.example.earnedtime.data.mapDAO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow

//Mapa de packetes y las IDs de su URL para los principales navegadores de android
private val PackageAndID = mapOf(
    // Google Chrome
    "com.android.chrome" to "com.android.chrome:id/url_bar",

    // Mozilla Firefox (Note: newer versions use the mozac component ID)
    "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",

    // Microsoft Edge
    "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",

    // DuckDuckGo Privacy Browser
    "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",

    // Brave Browser
    "com.brave.browser" to "com.brave.browser:id/url_bar",

    // Samsung Internet Browser
    "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",

    // Opera
    "com.opera.browser" to "com.opera.browser:id/url_field",

    // Opera Mini
    "com.opera.mini.native" to "com.opera.mini.native:id/url_field",

    // Yandex Browser
    "com.yandex.browser" to "com.yandex.browser:id/bro_omnibar_address_title_text"
)



fun dayKey(haceDias : Int): String  //0 para el de hoy
{
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -haceDias)  //Para cubrir una semana
    return  SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
}

class GestionUso : AccessibilityService() {

    val TiempoInicialNoti = 15 *60
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val dao: mapDAO by lazy { AppDatabase.getDatabase(this).mapDAO() }

    private var currentDomain: String? = null
    private var openTimestamp: Long = 0L
    private var pendingBlockRunnable: Runnable? = null

    private var notificationRunnable: Runnable? = null

    private var dominiosCache: List<DomainEntry> = emptyList()

    private var appsCache: List<AppEntry> = emptyList()

    private val screenOffReceiver = object : BroadcastReceiver() {  //De apagarse la pantalla, deja de contar
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                closeCurrentSession()
            }
        }
    }

    override fun onServiceConnected() {

        super.onServiceConnected()
        ContextCompat.registerReceiver( //Registra detector de apagado
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_EXPORTED
        )
        serviceScope.launch {
            dao.getDomains().collect { lista ->
                dominiosCache = lista
            }
        }

        serviceScope.launch {
            dao.getApps().collect { lista ->
                appsCache = lista
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
        closeCurrentSession()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        val type = event.eventType

        // filtrado de ruido
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) {
            return
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val activePkg = rootInActiveWindow?.packageName?.toString() ?: pkg

            if (!PackageAndID.contains(activePkg)) {  //No es una web
                val matchedApp = BlockeoConfig.matchApp(activePkg, appsCache)

                if(matchedApp==null)  //Si no se ha encontrado un match en la list de apps limitadas
                {
                    closeCurrentSession()
                    return
                }

                if (matchedApp.packageName == currentDomain) return   //Seguimos en la misma

                //Si es controlada, no baneada, empezamos a contar
                openSession(matchedApp.packageName, false, matchedApp.grupo)

                return
            }
        }

        val ID_Url = PackageAndID.get(pkg)

        if (ID_Url==null) return       //Cualquier otra app (no limitada), no importa


        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&     //Más ruido
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val urlBarText = readUrlBarText(ID_Url)

        val pareceUrl = !urlBarText.isNullOrBlank() && !urlBarText.contains(' ')
        if (!pareceUrl) {
            return
        }

        val matchedDomain = BlockeoConfig.matchDomain(urlBarText, dominiosCache)

        if(matchedDomain == null)  //Si no se ha encontrado un match en la list de dominios limitados
        {
            closeCurrentSession()
            return
        }

        if (matchedDomain.domain == currentDomain) { //Seguimos en el mismo
            return
        }


        //Si es controlado, no baneado, empezamos a contar
        openSession(matchedDomain.domain, true, matchedDomain.grupo)

    }

    private fun readUrlBarText(ID_URL : String): String? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_URL)
        val text = nodes?.firstOrNull()?.text?.toString()
        nodes?.forEach { it.recycle() }
        return text
    }

    private fun openSession(domain: String, web: Boolean, idGrupo: Int) {
        currentDomain = domain
        openTimestamp = System.currentTimeMillis()

        val today = dayKey(0)

        serviceScope.launch {
            val grupo = dao.getGrupo(idGrupo)
            if (grupo == null) {
                handler.post { if (currentDomain == domain) currentDomain = null }
                return@launch
            }
            val usado = dao.getTotalSecondsForGroup(idGrupo, today) ?: 0L
            var remaining = dao.getOrResetLimite(grupo) - usado

            dao.limpiarPausasCaducadas(System.currentTimeMillis())      //Quita las pausas espontaneas pasadas, si las hay
            val pausasRelevantes = dao.getPausasSingular().filter { idGrupo in it.grupos }  //Coge las pausas que afectan a este grupo
            var segsHastaPausa: Long? = null

            val now = Calendar.getInstance()
            val minutos = now.time.hours * 60 + now.time.minutes
            val hoy = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1     // para que 1 = lunes y 7 = domingo
            val ahora = System.currentTimeMillis()

            for(i in 0 .. pausasRelevantes.size-1)  //Para cada pausa
            {
                val p = pausasRelevantes[i]

                var Activa = false
                var hora = ""

                if (!p.programada) {    //Si es espontanea, y no ha sido limpiada, activa la pausa

                    val fin = p.inicioMillis + p.duracionMins * 60_000L

                    if (ahora >= p.inicioMillis && ahora < fin) {   //Compruebo por si acaso
                        Activa = true
                        hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(fin))
                    }

                } else {

                    for (dias in -1..6) {   //Va, en las programadas, de anteror a posterior, partiendo de ayer (así coge la activa, y si no hay una pone espera a la más próxima)
                        val d = Math.floorMod(hoy - 1 + dias, 7) + 1
                        if (d !in p.diasSem) continue

                        val inicio = dias * 1440 + p.progInicioMins - minutos

                        if (inicio <= 0 && inicio + p.duracionMins > 0) {   //Activa ahora mismo
                            Activa = true
                            val finMin = (p.progInicioMins + p.duracionMins) % 1440
                            hora = "%02d:%02d".format(finMin / 60, finMin % 60)
                            break
                        }
                        if (inicio > 0) {
                            val segs = inicio * 60L
                            if (segsHastaPausa == null || segs < segsHastaPausa) segsHastaPausa = segs  //Para el temporizador
                            break
                        }
                    }

                }

                if (Activa) {
                    val tipo = if (web) "web" else "App"
                    handler.post {
                        if (currentDomain != domain) return@post
                        blockNow(domain, 0L)
                        Toast.makeText(this@GestionUso, "Esta $tipo está pausada hasta las $hora", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            }

            handler.post {
                if (currentDomain != domain) return@post

                if (remaining <= 0) {
                    blockNow(domain, 0L)
                }
                else {

                    if(segsHastaPausa != null)
                    {
                        remaining = minOf(remaining, segsHastaPausa)    //Hasta que se acabe el tiempo de uso del grupo o empiece la siguiente pausa programada que lo afecte
                    }
                    scheduleBlock(domain, remaining, web)
                }
            }
        }
    }

    private fun scheduleBlock(domain: String, remainingSeconds: Long, web: Boolean) {
        cancelPendingBlock()
        val runnable = Runnable { blockNow(domain, elapsedSeconds = remainingSeconds) }
        pendingBlockRunnable = runnable
        handler.postDelayed(runnable, (remainingSeconds) * 1000)

        if(remainingSeconds> TiempoInicialNoti) //Preparamos notificación uso a los 15 minutos
        {
            val notiRunnable = Runnable { notificationLoop( domain,0) }
            notificationRunnable = notiRunnable
            handler.postDelayed(notiRunnable, TiempoInicialNoti*1000L)
        }
    }

    private fun notificationLoop(dominio: String,iteracion: Int) {  //Notificacion de tiempo usado, incremental
        val siguiente = TiempoInicialNoti*2.0.pow(iteracion).toInt()

        val titulo = "Uso de  ${dominio}"
        val TiempoString = formatTime(siguiente.toLong())
        val textContent = "Has utilizado ${dominio} durante ${TiempoString.subSequence(0,TiempoString.length-5)}"

        NotificationManager.PonerNotificacion(this, titulo, textContent)

        val notiRunnable = Runnable { notificationLoop( dominio,iteracion+1) }
        notificationRunnable = notiRunnable
        handler.postDelayed(notiRunnable, siguiente.toLong()*1000)    //15m, 30m, 1h...
    }

    private fun cancelPendingBlock() {
        pendingBlockRunnable?.let { handler.removeCallbacks(it) }   //Quitamos temporizador bloqueo
        pendingBlockRunnable = null

        notificationRunnable?.let { handler.removeCallbacks(it) }   //Quitamos temporizador notificación
        notificationRunnable = null
    }

    private fun closeCurrentSession() {
        cancelPendingBlock()
        val domain = currentDomain ?: return
        currentDomain = null

        val elapsed = (System.currentTimeMillis() - openTimestamp) / 1000
        if (elapsed <= 0) return

        val segundosHoy = LocalTime.now().toSecondOfDay()   //Si ha cambiado de dia en mitad del scheduled block, para que no te sume todo el uso, solo el correspondiente a hoy

        if(elapsed<segundosHoy) //Todo normal
        {
            serviceScope.launch { dao.persistUsage(domain, dayKey(0), elapsed) }
        }
        else    //Ha habido cambio de dia en medio del uso
        {
            serviceScope.launch {
                dao.persistUsage(domain, dayKey(0), segundosHoy.toLong())
                dao.persistUsage(domain, dayKey(1), elapsed - segundosHoy.toLong())
            }
        }

    }

    private fun blockNow(domain: String, elapsedSeconds: Long) {

        cancelPendingBlock()

        pendingBlockRunnable = Runnable {
            if (currentDomain == domain) {   // seguimos en lo mismo tras los 4 s
                currentDomain = null
                val today = dayKey(0)
                val segundosHoy = LocalTime.now().toSecondOfDay()   //Si ha cambiado de dia en mitad del scheduled block, para que no te sume todo el uso, solo el correspondiente a hoy

                if(elapsedSeconds<segundosHoy) //Todo normal
                {
                    serviceScope.launch { dao.persistUsage(domain, dayKey(0), elapsedSeconds) }
                }
                else    //Ha habido cambio de dia en medio del uso
                {
                    serviceScope.launch {
                        dao.persistUsage(domain, dayKey(0), segundosHoy.toLong())
                        dao.persistUsage(domain, dayKey(1), elapsedSeconds - segundosHoy.toLong())
                    }
                }

                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        handler.postDelayed(pendingBlockRunnable!!, 4_000)

    }

    override fun onInterrupt() {}
}