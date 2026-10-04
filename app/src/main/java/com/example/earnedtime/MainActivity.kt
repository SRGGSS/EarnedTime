package com.example.earnedtime

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.earnedtime.data.AppEntry
import com.example.earnedtime.data.DomainEntry
import com.example.earnedtime.data.ObjetivoEntry
import com.example.earnedtime.data.UsageEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

//Mostrado en la lista
data class ItemBloqueo(
    val dominio: String,
    val banned: Boolean,
    val esApp: Boolean,
    val domainEntry: DomainEntry? = null,
    val appEntry: AppEntry? = null
)

data class AppInstalada(
    val packageName: String,
    val nombre: String,
    val icon: Drawable
)



//https://docs.mapbox.com/android/maps/examples/compose/global-scale-factor/
fun toImageBitmap(drawable: Drawable): ImageBitmap {
    val ancho = drawable.intrinsicWidth.coerceAtLeast(1)
    val alto = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)

    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}


//https://tomas-repcik.medium.com/listing-all-installed-apps-in-android-13-via-packagemanager-3b04771dc73
fun listarAppsInstaladas(context: Context): List<AppInstalada> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)



    val resolvedInfos =if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(0L))
    } else {
        pm.queryIntentActivities(intent, 0)
    }

    return resolvedInfos
        .map { AppInstalada(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName } //Esta aplicación
        .sortedBy { it.nombre.lowercase() }
}
class MainActivity : ComponentActivity() {

    val viewModel = MainViewModel()

    enum class Pantalla(val icono: Int) {
        Sitios( R.drawable.ajustes),
        Desglose( R.drawable.lista),
        Objetivos( R.drawable.tareas)

    }

    fun formatTime(totalSeconds: Long): String {
        val horas = totalSeconds/3600
        val minutes = (totalSeconds%3600) / 60
        val seconds = totalSeconds % 60
        return if(horas >0) if (minutes > 0) "${horas}h ${minutes}m ${seconds}s" else "${horas}h ${seconds}s" else  if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        val context = this
        super.onCreate(savedInstanceState)

        setContent {

            LaunchedEffect(Unit)
            {
                viewModel.Cargar(context)
            }

            var enabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled(GestionUso::class.java)
            ) } //Permisos concedidos
            var pantallaActual by remember { mutableStateOf(Pantalla.Desglose) }    //Navegación entre 3 pantallas


            MaterialTheme {
                Scaffold(
                    bottomBar = {
                        if (enabled) {
                            NavigationBar(
                                containerColor = Color(240,240,242)
                            ) {
                                Pantalla.entries.forEach { pantalla ->
                                    NavigationBarItem(
                                        selected = pantallaActual == pantalla,
                                        onClick = { pantallaActual = pantalla },
                                        icon = {Icon(
                                            painter = painterResource(id = pantalla.icono),
                                            contentDescription = pantalla.name,
                                            modifier = Modifier.size(26.dp)
                                        )}
                                    )
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (enabled) {
                            when (pantallaActual) {
                                Pantalla.Objetivos -> PantallaObjetivos(viewModel.objetivos)
                                Pantalla.Desglose -> PantallaDesglose(viewModel.dominios, viewModel.limite)
                                Pantalla.Sitios -> PantallaDominios(viewModel.dominiosbloqueados, viewModel.appsBloqueadas)
                            }
                        } else {
                            PantallaSinPermiso(
                                onAbrirAjustes = { openAccessibilitySettings() },
                                onComprobar = { enabled = context.isAccessibilityServiceEnabled(GestionUso::class.java)
                                }
                            )
                        }
                    }
                }
            }
        }
    }


    @Composable
    fun PantallaObjetivos(objetivos: List<ObjetivoEntry>) {    //Lista de objetivos
        var mostrarDialogo by remember { mutableStateOf(false) }
        var objetivoEditando by remember { mutableStateOf<ObjetivoEntry?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            if (objetivos.isEmpty()) {
                Text(
                    text = "No tienes ningún objetivos. Pulsa + para añadir uno.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(objetivos, key = { it.id }) { objetivo ->
                    val hoy = todayKey()

                    val usoDiarioHoy = if (objetivo.dateCompletado == hoy) objetivo.usoDiario else 0
                    val completadoHoy = objetivo.LimiteDiario > 0 && usoDiarioHoy >= objetivo.LimiteDiario

                    val usoEstaSem = if (!haPasadoUnLunesDesde(objetivo.dateCompletado)) objetivo.usoSemanal else 0
                    val completadoEstaSem = objetivo.LimiteSemanal > 0 && usoEstaSem >= objetivo.LimiteSemanal

                    val bloqueado = completadoHoy || completadoEstaSem

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = bloqueado,    //Solo se muestra como checkeado si se alcanza el límite
                                onCheckedChange = { if (!bloqueado) viewModel.completarObjetivo(objetivo) }
                            )

                            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(text = objetivo.Objetivo, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "+${objetivo.TiempoExtra / 60} min · ${usoDiarioHoy}${if(objetivo.LimiteDiario>0) "/"+ objetivo.LimiteDiario else ""} hoy · ${usoEstaSem}${if(objetivo.LimiteSemanal>0)"/"+objetivo.LimiteSemanal else ""} esta semana",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { objetivoEditando = objetivo; mostrarDialogo = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = { viewModel.eliminarObjetivo(objetivo.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { objetivoEditando = null; mostrarDialogo = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir objetivo")
            }
        }

        if (mostrarDialogo) {
            DialogoObjetivo(
                objetivo = objetivoEditando,
                onGuardar = { entry -> viewModel.guardarObjetivo(entry); mostrarDialogo = false },
                onCancelar = { mostrarDialogo = false }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class) //Para el DatePicker
    @Composable
    fun DialogoObjetivo(
        objetivo: ObjetivoEntry?,
        onGuardar: (ObjetivoEntry) -> Unit,
        onCancelar: () -> Unit
    ) {
        var texto by remember { mutableStateOf(objetivo?.Objetivo ?: "") }
        var minutosExtra by remember { mutableStateOf(((objetivo?.TiempoExtra ?: 300L) / 60).toString()) }
        var limiteDiario by remember { mutableStateOf((objetivo?.LimiteDiario ?: 0).toString()) }
        var limiteSemanal by remember { mutableStateOf((objetivo?.LimiteSemanal ?: 0).toString()) }
        var fechaLimite by remember { mutableStateOf(objetivo?.fechaLimite ?: "") }
        var mostrarDatePicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text(if (objetivo == null) "Nuevo objetivo" else "Editar objetivo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it },
                        label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = minutosExtra,
                        onValueChange = { minutosExtra = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutos extra por completar") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = limiteDiario,
                            onValueChange = { limiteDiario = it.filter { c -> c.isDigit() } },
                            label = { Text("Limite diario") }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = limiteSemanal,
                            onValueChange = { limiteSemanal = it.filter { c -> c.isDigit() } },
                            label = { Text("Limite semanal") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (fechaLimite.isBlank()) "Sin fecha límite" else "Limite: $fechaLimite",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row {
                            TextButton(onClick = { mostrarDatePicker = true }) {
                                Text(if (fechaLimite.isBlank()) "Elegir fecha" else "Cambiar")
                            }
                            if (fechaLimite.isNotBlank()) {
                                TextButton(onClick = { fechaLimite = "" }) { Text("Quitar") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {

                        onGuardar(
                            ObjetivoEntry(
                                id = objetivo?.id ?: UUID.randomUUID().toString(),
                                Objetivo = texto,
                                TiempoExtra = (minutosExtra.toLongOrNull() ?: 0L) * 60,
                                LimiteDiario = limiteDiario.toIntOrNull() ?: 0,
                                LimiteSemanal = limiteSemanal.toIntOrNull() ?: 0,
                                usoDiario = objetivo?.usoDiario ?: 0,
                                usoSemanal = objetivo?.usoSemanal ?: 0,
                                dateCompletado = objetivo?.dateCompletado ?: "",
                                fechaLimite = fechaLimite
                            )
                        )

                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
        )

        if (mostrarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = dateKeyToMillis(fechaLimite) ?: System.currentTimeMillis()  //Autoselección de último elegido o hoy
            )
            DatePickerDialog(   //Selector de fecha
                onDismissRequest = { mostrarDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            fechaLimite = millisToDateKey(millis)
                        }
                        mostrarDatePicker = false
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    @Composable
    fun PantallaDesglose(dominios: List<UsageEntry>, limiteTotal: Long) {   //Desglose del consumo de tiempo
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

                // Calculamos el total gastado sumando todos los dominios
                val totalGastado = dominios.sumOf { it.secondsUsed }
                val progressGlobal = (totalGastado.toFloat() / limiteTotal.toFloat()).coerceIn(0f, 1f)

                val TextoUso = formatTime(totalGastado)
                val TextoLim = formatTime(limiteTotal)

                Column(modifier = Modifier.fillMaxWidth()) {

                    // Límite global
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Tiempo Total Gastado",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { progressGlobal },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp), //Ajustar grosor
                                color = if (progressGlobal >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Has gastado $TextoUso de $TextoLim",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )


                            Spacer(modifier = Modifier.height(26.dp))


                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(dominios) { entry ->

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val nombre = viewModel.MapaApps[entry.domain] ?: entry.domain   //Traduce el dominio de la aplicacion a esta
                                        Text(
                                            text = nombre,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = formatTime(entry.secondsUsed),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                            }
                        }
                    }



                }



        }
    }



    @Composable
    fun PantallaDominios(dominios: List<DomainEntry>, apps: List<AppEntry>) {   //Seleccion de Dominios controlados
        var mostrarElegirTipo by remember { mutableStateOf(false) }
        var mostrarDialogoDominio by remember { mutableStateOf(false) }
        var mostrarSelectorApps by remember { mutableStateOf(false) }
        var dominioEditando by remember { mutableStateOf<DomainEntry?>(null) }

        val items = remember(dominios, apps) {  //Se juntan aplicaciones y webs
            dominios.map { ItemBloqueo(it.domain, it.banned, false, domainEntry = it) } +
                    apps.map { ItemBloqueo(it.appLabel, it.banned, true, appEntry = it) }
        }


        //Se dividen entre baneados y no baneados
        val noBaneados = items.filter { !it.banned }.sortedBy { it.dominio.lowercase() }
        val baneados = items.filter { it.banned }.sortedBy { it.dominio.lowercase() }

        Box(modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                Text(
                    text = "No tienes sitios ni apps limitados. Pulsa + para añadir uno.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Text(
                        text = "Temporizados",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(noBaneados, key = { (if (it.esApp) "app:" else "web:") + it.dominio }) { item ->
                    FilaItemBloqueo(
                        item = item,
                        onToggleBanned = { nuevoValor ->
                            if (item.esApp) viewModel.guardarApp(item.appEntry!!.copy(banned = nuevoValor))
                            else viewModel.guardarDominio(item.domainEntry!!.copy(banned = nuevoValor))
                        },
                        onEliminar = {
                            if (item.esApp) viewModel.eliminarApp(item.appEntry!!.packageName)
                            else viewModel.eliminarDominio(item.domainEntry!!.domain)
                        }
                    )
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = "Baneados",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(baneados, key = { (if (it.esApp) "app:" else "web:") + it.dominio + "_b" }) { item ->
                    FilaItemBloqueo(
                        item = item,
                        onToggleBanned = { nuevoValor ->
                            if (item.esApp) viewModel.guardarApp(item.appEntry!!.copy(banned = nuevoValor))
                            else viewModel.guardarDominio(item.domainEntry!!.copy(banned = nuevoValor))
                        },
                        onEliminar = {
                            if (item.esApp) viewModel.eliminarApp(item.appEntry!!.packageName)
                            else viewModel.eliminarDominio(item.domainEntry!!.domain)
                        }
                    )
                }
            }

            FloatingActionButton(
                onClick = { mostrarElegirTipo = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir")
            }
        }

        if (mostrarElegirTipo) {
            DialogoElegirTipo(
                onElegirSitio = { mostrarElegirTipo = false; mostrarDialogoDominio = true },
                onElegirApp = { mostrarElegirTipo = false; mostrarSelectorApps = true },
                onCancelar = { mostrarElegirTipo = false }
            )
        }

        if (mostrarDialogoDominio) {
            DialogoDominio(
                onGuardar = { entry ->
                    val original = dominioEditando
                    if (original != null && original.domain != entry.domain) {
                        viewModel.eliminarDominio(original.domain)  //Se elimina el antiguo, en caso de que se haya editado
                    }
                    viewModel.guardarDominio(entry)
                    mostrarDialogoDominio = false
                },
                onCancelar = { mostrarDialogoDominio = false }
            )
        }

        if (mostrarSelectorApps) {
            SelectorDeApps(
                yaAgregadas = apps.map { it.packageName }.toSet(),
                onSeleccionar = { app ->
                    viewModel.guardarApp(AppEntry(app.packageName, app.nombre, banned = false))
                    mostrarSelectorApps = false
                },
                onCancelar = { mostrarSelectorApps = false }
            )
        }
    }


    @Composable
    fun SelectorDeApps(
        yaAgregadas: Set<String>,
        onSeleccionar: (AppInstalada) -> Unit,
        onCancelar: () -> Unit
    ) {
        val context = LocalContext.current
        var apps by remember { mutableStateOf<List<AppInstalada>>(emptyList()) }
        var busqueda by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) { listarAppsInstaladas(context) }
        }

        val filtradas = apps.filter {
            it.packageName !in yaAgregadas && it.nombre.contains(busqueda, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text("Elegir app") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        label = { Text("Buscar") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn {
                        items(filtradas, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeleccionar(app) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = toImageBitmap(app.icon),
                                    contentDescription = app.nombre,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(app.nombre)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
        )
    }

    @Composable
    fun DialogoDominio(
        onGuardar: (DomainEntry) -> Unit,
        onCancelar: () -> Unit
    ) {
        var texto by remember { mutableStateOf( "") }
        var baneado by remember { mutableStateOf( false) }

        AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text( "Nueva Web") },
            text = {
                Column {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        label = { Text("Web (ej: instagram.com)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bloqueo instantáneo (baneado)")
                        Switch(checked = baneado, onCheckedChange = { baneado = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (texto.isNotBlank()) {
                        onGuardar(DomainEntry(domain = texto.trim().lowercase(), banned = baneado))
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
        )
    }


    @Composable
    fun PantallaSinPermiso(onAbrirAjustes: () -> Unit, onComprobar: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Para que el bloqueo funcione, activa el servicio de accesibilidad.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAbrirAjustes) { Text("Abrir ajustes de accesibilidad") }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onComprobar) { Text("Comprobar de nuevo") }
        }
    }
    fun Context.isAccessibilityServiceEnabled(service: Class<out AccessibilityService>): Boolean {
        val accessibilityManager  = getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)

        return enabledServices.any {
            val serviceInfo = it.resolveInfo.serviceInfo
            serviceInfo.packageName == packageName && serviceInfo.name == service.name
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    @Composable
    fun DialogoElegirTipo(onElegirSitio: () -> Unit, onElegirApp: () -> Unit, onCancelar: () -> Unit) {
        AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text("¿Qué quieres añadir?") },
            text = {
                Column {
                    TextButton(onClick = onElegirSitio, modifier = Modifier.fillMaxWidth()) { Text("Sitio web") }
                    TextButton(onClick = onElegirApp, modifier = Modifier.fillMaxWidth()) { Text("Aplicación") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
        )
    }


    @Composable
    fun FilaItemBloqueo(
        item: ItemBloqueo,
        onToggleBanned: (Boolean) -> Unit,
        onEliminar: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.dominio,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (item.esApp) "App" else "Web",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                Switch(checked = item.banned, onCheckedChange = onToggleBanned)

                IconButton(onClick = onEliminar, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}