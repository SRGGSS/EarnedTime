package com.example.earnedtime

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earnedtime.data.AppDatabase
import com.example.earnedtime.data.AppEntry
import com.example.earnedtime.data.DomainEntry
import com.example.earnedtime.data.GrupoEntry
import com.example.earnedtime.data.Limite
import com.example.earnedtime.data.ObjetivoEntry
import com.example.earnedtime.data.UsageEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat


import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale


//Se encarga de comprobar si el dominio actual está controlado, devuelve el dominio controlado que le corresponde y si está baneado o no
object BlockeoConfig {
    fun matchDomain(urlBarText: String?, domains: List<DomainEntry>): DomainEntry? {
        if (urlBarText.isNullOrEmpty()) return null
        val normalized = urlBarText.lowercase()

        //Contiene
        val contiene = domains.firstOrNull { normalized.contains(it.domain) }       //Mejora Pendiente: prioridad, para evitar conflictos si múltiples coinciden
        if(contiene == null) return null


        //Más garantía que un simple contiene, es una parte completa (evita errores como x.com -> stockx.com)
        val CharsDeInicio = listOf(".", "/", ":", "?", "#")

        val inicio = normalized.indexOf(contiene.domain)

        if(inicio != 0 && !CharsDeInicio.contains(normalized.get(inicio-1).toString())){
            return null
        }


        return contiene
    }

    fun matchApp(pkg: String, apps: List<AppEntry>): AppEntry? {
        val entry = apps.firstOrNull { it.packageName == pkg } ?: return null
        return entry
    }
}


//Comprobación para límites semanales
fun haPasadoUnLunesDesde(fechaAnteriorStr: String): Boolean {

    if (fechaAnteriorStr.isBlank()) return true

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val dateAnterior = sdf.parse(fechaAnteriorStr) ?: return false
    val dateHoy = sdf.parse(todayKey()) ?: return false
    val calAnterior = Calendar.getInstance().apply { time = dateAnterior }
    val calHoy = Calendar.getInstance().apply { time = dateHoy }

    // Si la fecha anterior es igual o posterior a hoy, no ha pasado ningún lunes
    if (!calAnterior.before(calHoy)) return false


    calAnterior.add(Calendar.DAY_OF_YEAR, 1) // Comprobación en bucle desde el día siguiente

    var i = 0
    while (!calAnterior.after(calHoy)) {
        if (calAnterior.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
            return true
        }
        calAnterior.add(Calendar.DAY_OF_YEAR, 1)
        i++

        // Han pasado 7 días, ha pasado un lunes
        if (i >= 7) return true
    }

    return false
}

fun millisToDateKey(millis: Long): String {
    val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return String.format(
        Locale.getDefault(),
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1, // Los meses empiezan en 0
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

fun dateKeyToMillis(dateKey: String): Long? {
    if (dateKey.isBlank()) return null
    try {
        val partes = dateKey.split("-")
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(partes[0].toInt(), partes[1].toInt() - 1, partes[2].toInt())
        return cal.timeInMillis
    } catch (e: Exception) {
        return null
    }
}
class MainViewModel : ViewModel() {
    var dominios by mutableStateOf<List<UsageEntry>>(emptyList())

    var dominiosbloqueados by mutableStateOf<List<DomainEntry>>(emptyList())
    var objetivos by mutableStateOf<List<ObjetivoEntry>>(emptyList())

    var appsBloqueadas by mutableStateOf<List<AppEntry>>(emptyList())

    var grupos by mutableStateOf<List<GrupoEntry>>(emptyList())
    var MapaApps = mutableMapOf<String, String>()


    private lateinit var context: Context
    fun Cargar(c: Context)
    {
        context = c.applicationContext  //applicationContext para garantizar que se mantiene independientemente de la clase que llama cargar()
        val dao = AppDatabase.getDatabase(context).mapDAO()

        viewModelScope.launch {
            dao.getALL(todayKey()).collect { lista ->
                dominios = lista
            }
        }

        viewModelScope.launch {
            dao.getDomains().collect { lista ->
                dominiosbloqueados = lista
            }
        }

        viewModelScope.launch {
            dao.limpiarObjetivosCaducados()
            dao.getObjetivos().collect { lista ->
                objetivos = lista
            }
        }

        viewModelScope.launch {
            dao.getApps().collect { lista ->
                appsBloqueadas = lista
                lista.forEach {
                    if(!MapaApps.contains(it.packageName)){
                        MapaApps[it.packageName] = it.appLabel      //Se guarda nombre de app, para mostrar este al usuario
                    }
                }
            }
        }

        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(context).mapDAO()
            dao.getGrupos().collect { lista ->
                lista.forEach { dao.getOrResetLimite(it) }   // Para mantener la lista actualizada
                grupos = lista
            }
        }

    }

    fun guardarObjetivo(entry: ObjetivoEntry) {
        viewModelScope.launch {
            AppDatabase.getDatabase(context).mapDAO().setObjetivo(entry)
        }
    }

    fun eliminarObjetivo(id: String) {
        viewModelScope.launch {
            AppDatabase.getDatabase(context).mapDAO().deleteObjetivo(id)
        }
    }

    fun guardarDominio(entry: DomainEntry) {
        viewModelScope.launch {
            AppDatabase.getDatabase(context).mapDAO().addDomain(entry)
        }
    }

    fun eliminarDominio(entry: String) {
        viewModelScope.launch {
            AppDatabase.getDatabase(context).mapDAO().deleteDomain(entry)
        }
    }

    fun guardarApp(entry: AppEntry) {
        viewModelScope.launch {
            AppDatabase.getDatabase(context).mapDAO().addApp(entry)
        }
    }

    fun eliminarApp(packageName: String) {
        viewModelScope.launch {
            AppDatabase.getDatabase(context).mapDAO().deleteApp(packageName)
        }
    }

    fun completarObjetivo(entry: ObjetivoEntry) {
        val hoy = todayKey()

        val usoDiarioActual = if (entry.dateCompletado == hoy) entry.usoDiario else 0
        val limDiario = entry.LimiteDiario

        val usoSemanalAcatual = if (!haPasadoUnLunesDesde(entry.dateCompletado)) entry.usoSemanal else 0
        val limSemanal = entry.LimiteSemanal

        if (limDiario>0 && usoDiarioActual >= limDiario || (limSemanal>0 && usoSemanalAcatual >= limSemanal)) return

        val actualizado = entry.copy(usoDiario = usoDiarioActual + 1,usoSemanal = usoSemanalAcatual + 1, dateCompletado = hoy)

        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(context).mapDAO()
            dao.setObjetivo(actualizado)

            val grupo = dao.getGrupo(entry.grupo) ?: return@launch
            dao.setGrupo(grupo.copy(limite = grupo.limite.copy(LimiteActualSegs = grupo.limite.LimiteActualSegs + entry.TiempoExtra)))  //Se actualiza el límite del grupo asociado
        }
    }


    fun guardarGrupo(g: GrupoEntry) {
        viewModelScope.launch { AppDatabase.getDatabase(context).mapDAO().setGrupo(g) }
    }

    fun eliminarGrupo(id: String) {
        viewModelScope.launch { AppDatabase.getDatabase(context).mapDAO().deleteGrupo(id) }
    }

    fun usosDeGrupo(id: String): Flow<List<UsageEntry>> {
        return AppDatabase.getDatabase(context).mapDAO().getEntriesForGroup(id, todayKey())
    }
}