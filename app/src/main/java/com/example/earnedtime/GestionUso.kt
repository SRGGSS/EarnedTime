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
import com.example.earnedtime.data.mapDAO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CHROME_PACKAGE = "com.android.chrome"
private const val URL_BAR_ID = "com.android.chrome:id/url_bar"

fun todayKey(): String =  SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
class GestionUso : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val dao: mapDAO by lazy { AppDatabase.getDatabase(this).mapDAO() }

    private var currentDomain: String? = null
    private var openTimestamp: Long = 0L
    private var pendingBlockRunnable: Runnable? = null

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

            if (activePkg != CHROME_PACKAGE) {  //No es una web
                val matchedApp = BlockeoConfig.matchApp_Banned(activePkg, appsCache)

                if (matchedApp.first == currentDomain) return   //Seguimos en la última

                if (matchedApp.second) {    //Baneado
                    closeCurrentSession()
                    currentDomain = matchedApp.first
                    openTimestamp = System.currentTimeMillis()
                    blockNow(matchedApp.first!!,0L,false)
                    return
                }

                closeCurrentSession()
                if (matchedApp.first != null) {
                    openSession(matchedApp.first!!, false)
                }
                return
            }
        }


        if (pkg != CHROME_PACKAGE) return       //Cualquier otra app (no limitada), no importa


        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&     //Más ruido
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val urlBarText = readUrlBarText()

        val pareceUrl = !urlBarText.isNullOrBlank() && !urlBarText.contains(' ')
        if (!pareceUrl) {
            return
        }

        val matchedDomain = BlockeoConfig.matchDomain_Banned(urlBarText, dominiosCache)

        if (matchedDomain.first == currentDomain) { //Seguimos en la última
            return
        }

        if(matchedDomain.second)        //Baneada
        {
            closeCurrentSession()
            currentDomain = matchedDomain.first
            openTimestamp = System.currentTimeMillis()
            blockNow(matchedDomain.first!!,0L,true)
            return
        }

        // Ha cambiado, dejamos de contar
        closeCurrentSession()

        if (matchedDomain.first != null) {  //Si es controlada, no baneada, empezamos a contar
            openSession(matchedDomain.first!!, true)
        }
    }

    private fun readUrlBarText(): String? {
        val root = rootInActiveWindow ?: return null
        val nodes = root.findAccessibilityNodeInfosByViewId(URL_BAR_ID)
        val text = nodes?.firstOrNull()?.text?.toString()
        nodes?.forEach { it.recycle() }
        return text
    }

    private fun openSession(domain: String, web: Boolean) {
        currentDomain = domain
        openTimestamp = System.currentTimeMillis()

        val today = todayKey()

        serviceScope.launch {

            val totalUsedSoFar = dao.getTotalSecondsForDate(today) ?: 0L    //tiempo total hoy
            val limite = dao.getOrResetLimite()
            val remaining = limite - totalUsedSoFar

            handler.post {
                if (currentDomain != domain) return@post

                if (remaining <= 0) {
                    blockNow(domain,0L,web)
                } else {
                    scheduleBlock(domain, remaining, web)   //Temporizador
                }
            }
        }
    }

    private fun scheduleBlock(domain: String, remainingSeconds: Long, web: Boolean) {
        cancelPendingBlock()
        val runnable = Runnable { blockNow(domain, elapsedSeconds = remainingSeconds, web) }
        pendingBlockRunnable = runnable
        handler.postDelayed(runnable, (remainingSeconds) * 1000)
    }

    private fun cancelPendingBlock() {
        pendingBlockRunnable?.let { handler.removeCallbacks(it) }
        pendingBlockRunnable = null
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
        pendingBlockRunnable = null

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