package com.example.earnedtime.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.earnedtime.todayKey
import kotlinx.coroutines.flow.Flow

@Dao
interface mapDAO {

    /**GRUPOS**/
    @Query("SELECT * FROM grupo_entries")
    fun getGrupos(): Flow<List<GrupoEntry>>

    @Query("SELECT * FROM grupo_entries WHERE id = :ID ")
    suspend fun getGrupo(ID: String): GrupoEntry?

    @Query("DELETE FROM grupo_entries WHERE id = :id")      //No se debe llamar, solo se usa dentro de deleteGrupo()
    suspend fun deleteGrupoEntr(id: String)

    @Query("DELETE FROM dominio_entries WHERE grupo = :id")
    suspend fun deleteDomainsOfGroup(id: String)
    @Query("DELETE FROM app_entries WHERE grupo = :id")
    suspend fun deleteAppsOfGroup(id: String)

    suspend fun deleteGrupo(id: String) {
        deleteDomainsOfGroup(id)
        deleteAppsOfGroup(id)
        deleteGrupoEntr(id)
    }


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setGrupo(grupo: GrupoEntry)


    // Usos de hoy de los miembros del grupo, flow para sincronización
    @Query("SELECT u.* FROM usage_entries u INNER JOIN (SELECT domain, MAX(date) AS maxDate FROM usage_entries GROUP BY domain) latest ON u.domain = latest.domain AND u.date = latest.maxDate WHERE u.date >= :d AND ( u.domain IN (SELECT domain FROM dominio_entries WHERE grupo = :i) OR u.domain IN (SELECT packageName FROM app_entries WHERE grupo = :i))")
    fun getEntriesForGroup(i: String, d: String): Flow<List<UsageEntry>>

    @Query("SELECT SUM(secondsUsed) FROM usage_entries WHERE date = :date AND (domain IN (SELECT domain FROM dominio_entries WHERE grupo = :id) OR domain IN (SELECT packageName FROM app_entries WHERE grupo = :id))") //Revisar: Hacer que coja la posterior, a partir de hoy, no siempre hoy
    suspend fun getTotalSecondsForGroup(id: String, date: String): Long?

    /** APPS **/
    @Query("SELECT * FROM app_entries")
    fun getApps(): Flow<List<AppEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addApp(entry: AppEntry)

    @Query("DELETE FROM app_entries WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    /** USAGE **/

    //El tiempo de uso más reciente
    @Query("SELECT * FROM usage_entries WHERE domain = :domain ORDER BY date DESC LIMIT 1")
    suspend fun getEntry(domain: String): UsageEntry?

    //Todos los usos, mayor fecha a partir de x (normalmente la de hoy, en caso de que hayan usos con mayores fechas se utilizarán esos)
    @Query("SELECT u.* FROM usage_entries u INNER JOIN (SELECT domain, MAX(date) AS maxDate FROM usage_entries GROUP BY domain) latest ON u.domain = latest.domain AND u.date = latest.maxDate WHERE u.date >= :d")
    fun getALL(d: String): Flow<List<UsageEntry>>


    @Query("SELECT SUM(secondsUsed) FROM usage_entries WHERE date = :date")
    suspend fun getTotalSecondsForDate(date: String): Long?

    //Introducir/editar uso
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun SetUsage(entry: UsageEntry)

    @Query("UPDATE usage_entries SET secondsUsed = secondsUsed + :seconds WHERE domain = :domain AND date = :date")
    suspend fun addSeconds(domain: String, date: String, seconds: Long)

    /** DOMINIOS **/

    @Query("SELECT * FROM dominio_entries")
    fun getDomains(): Flow<List<DomainEntry>>

    @Query("DELETE FROM dominio_entries WHERE domain = :domain")
    suspend fun deleteDomain(domain: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDomain(entry: DomainEntry)


    /** OBJETIVOS **/
    @Query("SELECT * FROM objective_entries")
    fun getObjetivos(): Flow<List<ObjetivoEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setObjetivo(entry: ObjetivoEntry)

    @Query("DELETE FROM objective_entries WHERE id = :id")
    suspend fun deleteObjetivo(id: String)

    //Para única petición
    @Query("SELECT * FROM objective_entries")
    suspend fun getObjetivosOnce(): List<ObjetivoEntry>


    //Borrar objetivos con fecha límite anterior a hoy
    suspend fun limpiarObjetivosCaducados() {
        val hoy = todayKey()
        getObjetivosOnce().forEach { obj ->
            if (obj.fechaLimite.isNotBlank() && hoy > obj.fechaLimite) {
                deleteObjetivo(obj.id)
            }
        }
    }

    //Actualiza el uso de un dominio, si ya existe para esta fecha lo actualiza, si no lo crea
    suspend fun persistUsage(domain: String, date: String, seconds: Long) {
        if (seconds <= 0) return
        val existing = getEntry(domain)
        if (existing == null || date > existing.date) {
            SetUsage(UsageEntry(domain, date, seconds))
        } else {
            addSeconds(domain, existing.date, seconds)
        }
    }

    //Si el último límite no es actual se resetea
    suspend fun getOrResetLimite(grupo: GrupoEntry): Long {
        val hoy = todayKey()
        if (grupo.limite.date < hoy) {
            val nuevo = grupo.copy(limite = grupo.limite.copy(LimiteActualSegs = grupo.limite.LimiteBaseSegs, date = hoy))
            setGrupo(nuevo)
            return nuevo.limite.LimiteActualSegs
        }
        return grupo.limite.LimiteActualSegs
    }
}