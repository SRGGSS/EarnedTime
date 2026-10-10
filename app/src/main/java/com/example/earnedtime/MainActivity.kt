package com.example.earnedtime

import android.Manifest
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toComposePathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.earnedtime.data.AppEntry
import com.example.earnedtime.data.DomainEntry
import com.example.earnedtime.data.GrupoEntry
import com.example.earnedtime.data.ObjetivoEntry
import com.example.earnedtime.data.UsageEntry
import com.example.earnedtime.ui.theme.EarnedTimeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import com.example.earnedtime.data.Limite


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

fun formatTime(totalSeconds: Long): String {
    val horas = totalSeconds/3600
    val minutes = (totalSeconds%3600) / 60
    val seconds = totalSeconds % 60
    return if(horas >0) if (minutes > 0) "${horas}hrs ${minutes}mins ${seconds}s" else "${horas}hrs ${seconds}s" else  if (minutes > 0) "${minutes}mins ${seconds}s" else "${seconds}s"
}
class MainActivity : ComponentActivity() {

    val viewModel = MainViewModel()

    enum class Pantalla(val icono: Int) {
        Sitios( R.drawable.ajustes),
        Desglose( R.drawable.lista),
        Objetivos( R.drawable.tareas)

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


            EarnedTimeTheme(dynamicColor = false) {
                Scaffold(
                    bottomBar = {
                        if (enabled) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.height(76.dp),

                            ) {
                                Pantalla.entries.forEach { pantalla ->
                                    NavigationBarItem(
                                        selected = pantallaActual == pantalla,
                                        onClick = { pantallaActual = pantalla },
                                        icon = {
                                            Icon(
                                                painter = painterResource(id = pantalla.icono),
                                                contentDescription = pantalla.name,
                                                modifier = Modifier.size(29.dp),
                                                tint = if (pantallaActual == pantalla) Color.LightGray else MaterialTheme.colorScheme.surface,
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (enabled) {
                            when (pantallaActual) {
                                Pantalla.Objetivos -> PantallaObjetivos(viewModel.objetivos)
                                Pantalla.Desglose -> PantallaDesglose(viewModel.grupos)
                                Pantalla.Sitios -> PantallaGrupos(viewModel.grupos, viewModel.dominiosbloqueados, viewModel.appsBloqueadas)
                            }
                        } else {
                            PantallaSinPermiso(
                                onAbrirAjustes = { openAccessibilitySettings() },
                                onEnabled = {
                                    enabled = true
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

    val Colores = listOf(
        Color(0xFF61aee8),
        Color(0xFFeb3d46)
    )



    @Composable
    fun NotebookListCard(
        headerTitle: String,
        modifier: Modifier = Modifier,
        color: Color,
        grupo: GrupoEntry
    ) {


        val Usages by viewModel.usosDeGrupo(grupo.id).collectAsState(initial = emptyList())     //Pendiente: A lo mejor (?), no mostrar solo usos, si no dominios y su tiempo actual

        if(Usages.isEmpty()) return


        val totalGastado = Usages.sumOf { it.secondsUsed }
        val limiteTotal = grupo.limite.LimiteActualSegs
        val progressGlobal = (totalGastado.toFloat() / limiteTotal.toFloat()).coerceIn(0f, 1f)  //Porción consumida (0-1)

        val TextoUso = formatTime(totalGastado)
        val TextoLim = formatTime(limiteTotal)

        // Contenedor principal
        Column(
            modifier = modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false
                )
                .drawWithCache {    //Iluminación arriba y bordes imperfectos


                    val fade = blueFade(color, 15.dp.toPx())

                    val wobblyEffect = android.graphics.DiscretePathEffect(20f, 1.5f).toComposePathEffect()

                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 7.dp.toPx(),
                        pathEffect = wobblyEffect
                    )

                    onDrawWithContent {
                        drawContent()
                        drawRoundRect(
                            brush = fade,
                            style = stroke,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                        )
                    }
                }
                .clip(RoundedCornerShape(24.dp))
        ) {
            // Cabecera
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(blueFade(color, 15.dp.toPx())) }
                    .padding(vertical = 4.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {
                    Text(
                        text = headerTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ){
                        LinearProgressIndicator(
                            progress = { progressGlobal },
                            drawStopIndicator = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(11.dp),

                            color = MaterialTheme.colorScheme.onSurface,
                            trackColor = color,
                            gapSize = 0.dp,
                        )

                        LinearProgressIndicator(        //Sirve como track de la linea de progreso real. Utilizar solo track color en la original no daba el resultado esperado
                            progress = { 0f },
                            drawStopIndicator = {}, //Para quitar el punto final
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(11.dp),

                            color = Color.White.copy(alpha = 0.15f),
                            trackColor = Color.White.copy(alpha = 0.15f),
                            gapSize = 0.dp,
                        )


                    }

                    //Subtitulo
                    Text(
                        text = "Has gastado $TextoUso de $TextoLim",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))
                }

            }

            // Listado
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface)
                    .drawWithCache {

                        val wobblyEffect = android.graphics.DiscretePathEffect(20f, 1.5f).toComposePathEffect()

                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke( //Mismo efecto desde el espacio de listado de dentro
                            width = 8.dp.toPx(),
                            pathEffect = wobblyEffect
                        )

                        onDrawWithContent {
                            drawContent()

                            drawRoundRect(
                                color = color,
                                style = stroke,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                            )
                        }
                    }
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))


                Usages.forEachIndexed { index, usage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween

                    ) {

                        val nombre = viewModel.MapaApps[usage.domain] ?: usage.domain   //Traduce el dominio de la aplicacion a esta


                        Text(
                            text = nombre,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )

                        Text(
                            text = formatTime(usage.secondsUsed),   //Mirar si es preferible otro formato
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    // Línea separadora
                    if (index < Usages.size - 1) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
    @Composable
    fun PantallaDesglose(grupos: List<GrupoEntry>) {   //Desglose del consumo de tiempo

        Card(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            )
            {
                itemsIndexed(grupos) { index, item ->

                NotebookListCard(
                        headerTitle = "Registro de Tareas",
                        color = Colores.get(index%Colores.size),    //Bucle, reinicia una vez llega al fondo de los colores
                        grupo = item
                    )

                }
            }
        }
    }


    fun blueFade(base: Color, fadeEnd: Float) = Brush.verticalGradient(
        0f to lerp(base, Color.White, 0.25f),   // Más claro arriba.
        1f to base,                              // El resto tiene el color sólido.
        startY = 0f,
        endY = fadeEnd
    )


    @Composable
    fun PantallaGrupos(grupos: List<GrupoEntry>, dominios: List<DomainEntry>, apps: List<AppEntry>) {
        var grupoEditando by remember { mutableStateOf<GrupoEntry?>(null) }
        var mostrarDialogoGrupo by remember { mutableStateOf(false) }
        var grupoAnadirId by remember { mutableStateOf<String?>(null) }   // grupo al que se añade un miembro
        var mostrarElegirTipo by remember { mutableStateOf(false) }
        var mostrarDialogoDominio by remember { mutableStateOf(false) }
        var mostrarSelectorApps by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            if (grupos.isEmpty()) {
                Text(
                    text = "No tienes ningún grupo. Pulsa + para crear uno.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            }

            LazyColumn( //Listado de grupos
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
            ) {
                items(grupos, key = { it.id }) { grupo ->

                    val webs = dominios.filter { it.grupo == grupo.id }.sortedBy { it.domain }
                    val appsGrupo = apps.filter { it.grupo == grupo.id }.sortedBy { it.appLabel.lowercase() }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp))
                        {
                            Row(verticalAlignment = Alignment.CenterVertically)
                            {

                                Column(modifier = Modifier.weight(1f))
                                {
                                    Text(
                                        text = grupo.nombre,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text= "Límite diario: ${formatTime(grupo.limite.LimiteBaseSegs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { grupoEditando = grupo; mostrarDialogoGrupo = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar grupo")
                                }
                                IconButton(onClick = { viewModel.eliminarGrupo(grupo.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar grupo")
                                }
                            }

                            webs.forEach { web ->
                                FilaMiembro(
                                    nombre = web.domain,
                                    tipo = "Web",
                                    onEliminar = {viewModel.eliminarDominio(web.domain)}
                                )

                            }

                            appsGrupo.forEach { app ->
                                FilaMiembro(
                                    nombre = app.appLabel,
                                    tipo = "App",
                                    onEliminar = {viewModel.eliminarApp(app.packageName)}
                                )
                            }

                            TextButton(
                                onClick = {
                                    grupoAnadirId = grupo.id
                                    mostrarElegirTipo = true
                                }
                            )
                            {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Añadir a grupo",
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                Text(text = "Añadir web o app")
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { grupoEditando = null; mostrarDialogoGrupo = true },
                modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir grupo")
            }
        }

        if (mostrarDialogoGrupo) {
            DialogoGrupo(
                grupo = grupoEditando,
                onGuardar = {
                    viewModel.guardarGrupo(it)
                    mostrarDialogoGrupo = false },

                onCancelar = { mostrarDialogoGrupo = false }
            )
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
                    grupoAnadirId?.let { viewModel.guardarDominio(entry.copy(grupo = it)) }
                    mostrarDialogoDominio = false
                },
                onCancelar = { mostrarDialogoDominio = false }
            )
        }

        if (mostrarSelectorApps) {
            SelectorDeApps(
                yaAgregadas = apps.map { it.packageName }.toSet(),   // una app solo puede estar en un grupo
                onSeleccionar = { app ->
                    grupoAnadirId?.let {
                        viewModel.guardarApp(AppEntry(app.packageName, app.nombre, grupo = it))
                    }
                    mostrarSelectorApps = false
                },
                onCancelar = { mostrarSelectorApps = false }
            )
        }
    }

    @Composable
    fun FilaMiembro(nombre: String, tipo: String, onEliminar: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text= nombre,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            Text(
                text = tipo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onEliminar,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Quitar", modifier = Modifier.size(18.dp))
            }
        }
    }

    @Composable
    fun DialogoGrupo(grupo: GrupoEntry?, onGuardar: (GrupoEntry) -> Unit, onCancelar: () -> Unit) {

        var nombre by remember { mutableStateOf(grupo?.nombre ?: "") }
        var minutos by remember { mutableStateOf(((grupo?.limite?.LimiteBaseSegs ?: 1800L) / 60).toString()) }

        AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text(if (grupo == null) "Nuevo grupo" else "Editar grupo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it },
                        label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = minutos,
                        onValueChange = { minutos = it.filter { c -> c.isDigit() } },
                        label = { Text("Límite diario (minutos)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {

                    val base = (minutos.toLongOrNull() ?: 0L) * 60

                    if (nombre.isNotBlank() && base > 0) {

                        // en caso de que ya hubiera extra de hoy
                        val extra = grupo?.let { it.limite.LimiteActualSegs } ?: base

                        onGuardar(
                            GrupoEntry(
                                id = grupo?.id ?: UUID.randomUUID().toString(),
                                nombre = nombre.trim(),
                                limite = Limite(
                                    LimiteBaseSegs = base,
                                    LimiteActualSegs = extra,
                                    date = grupo?.limite?.date ?: todayKey()
                                )
                            )
                        )
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
        )
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
    fun PantallaSinPermiso(onAbrirAjustes: () -> Unit, onEnabled: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val context = LocalContext.current

            val permissionLauncher = rememberLauncherForActivityResult(     //https://developer.android.com/develop/ui/compose/notifications/notification-permission?hl=es-419
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {

                } else {

                }
            }


            Text(text = "Para que el bloqueo funcione, activa el servicio de accesibilidad.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAbrirAjustes) { Text("Abrir ajustes de accesibilidad") }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick =
                {
                    if(context.isAccessibilityServiceEnabled(GestionUso::class.java))
                    {
                        //En caso de que se haya concedido se pidel el resto de permisos
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        onEnabled()
                    }
                }
            ) { Text("Comprobar de nuevo") }
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



}