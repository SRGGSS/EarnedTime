package com.example.earnedtime

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.earnedtime.data.AppDatabase
import com.example.earnedtime.data.AppEntry
import com.example.earnedtime.data.DomainEntry
import com.example.earnedtime.data.GrupoEntry
import com.example.earnedtime.data.mapDAO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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



fun todayKey(): String =  SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
class GestionUso : AccessibilityService() {

    val TiempoInicialNoti = 15 *60;
    val NotiManager = NotificationManager() //Se enviarán notificaciones desde aquí
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

        if(!NotiManager.Iniciado)
        {
            NotiManager.createNotificationChannel(this)     //Se crea el canal de notificaciones
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

                if(matchedApp==null || matchedApp.grupo.isBlank())  //Si no se ha encontrado un match en la list de apps limitadas
                {
                    closeCurrentSession()
                    return
                }

                if (matchedApp.packageName == currentDomain) return   //Seguimos en la misma

                if (matchedApp.banned) {    //Baneado
                    closeCurrentSession()
                    currentDomain = matchedApp.packageName
                    openTimestamp = System.currentTimeMillis()
                    blockNow(matchedApp.packageName,0L,false)
                    return
                }

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

        if(matchedDomain == null || matchedDomain.grupo.isBlank())  //Si no se ha encontrado un match en la list de dominios limitados
        {
            closeCurrentSession()
            return
        }

        if (matchedDomain.domain == currentDomain) { //Seguimos en el mismo
            return
        }

        if(matchedDomain.banned)        //Baneada
        {
            closeCurrentSession()
            currentDomain = matchedDomain.domain
            openTimestamp = System.currentTimeMillis()
            blockNow(matchedDomain.domain,0L,true)
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

    private fun openSession(domain: String, web: Boolean, idGrupo: String) {
        currentDomain = domain
        openTimestamp = System.currentTimeMillis()

        val today = todayKey()

        serviceScope.launch {
            val grupo = dao.getGrupo(idGrupo)
            if (grupo == null) {
                handler.post { if (currentDomain == domain) currentDomain = null }
                return@launch
            }
            val usado = dao.getTotalSecondsForGroup(idGrupo, today) ?: 0L
            val remaining = dao.getOrResetLimite(grupo) - usado

            handler.post {
                if (currentDomain != domain) return@post

                if (remaining <= 0) {
                    blockNow(domain, 0L, web)
                }
                else {
                    scheduleBlock(domain, remaining, web)
                }
            }
        }
    }

    private fun scheduleBlock(domain: String, remainingSeconds: Long, web: Boolean) {
        cancelPendingBlock()
        val runnable = Runnable { blockNow(domain, elapsedSeconds = remainingSeconds, web) }
        pendingBlockRunnable = runnable
        handler.postDelayed(runnable, (remainingSeconds) * 1000)

        if(remainingSeconds> TiempoInicialNoti) //Preparamos notificación uso a los 15 minutos
        {
            val notiRunnable = Runnable { notificationLoop( domain,0) }
            notificationRunnable = notiRunnable
            handler.postDelayed(notiRunnable, TiempoInicialNoti*1000L)
        }
    }

    private fun notificationLoop(domain: String,iteracion: Int) {
        val siguiente = TiempoInicialNoti*2.0.pow(iteracion).toInt()
        NotiManager.PonerNotificacion(this, domain, (siguiente))

        val notiRunnable = Runnable { notificationLoop( domain,iteracion+1) }
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

        val today = todayKey()
        serviceScope.launch { dao.persistUsage(domain, today, elapsed) }
    }

    private fun blockNow(domain: String, elapsedSeconds: Long, web: Boolean) {
        currentDomain = null
        cancelPendingBlock()

        val today = todayKey()
        serviceScope.launch { dao.persistUsage(domain, today, elapsedSeconds) }

        if(web)
        {
            //Sale de la página
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) };
            startActivity(browserIntent);
        }
        else
        {
            //Cierra la aplicación
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}