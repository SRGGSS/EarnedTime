package com.example.earnedtime

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.earnedtime.data.AppEntry
import com.example.earnedtime.data.DomainEntry
import com.example.earnedtime.data.GrupoEntry
import com.example.earnedtime.data.Limite
import com.example.earnedtime.data.ObjetivoEntry
import com.example.earnedtime.ui.theme.EarnedTimeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.AccessController.getContext
import java.util.UUID


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

    val canvas = Canvas(bitmap)
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
    return if(horas >0) if (minutes > 0) "${horas}hrs ${minutes}min ${seconds}s" else "${horas}hrs ${seconds}s" else  if (minutes > 0) "${minutes}min ${seconds}s" else "${seconds}s"
}
class MainActivity : ComponentActivity() {

    val viewModel = MainViewModel()

    enum class Pantalla(val icono: Int) {
        Sitios( R.drawable.ajustes),
        Desglose( R.drawable.lista),
        Objetivos( R.drawable.tareas)

    }

    var apps by mutableStateOf<List<AppInstalada>>(emptyList())


    override fun onCreate(savedInstanceState: Bundle?) {

        val context = this
        super.onCreate(savedInstanceState)



        setContent {

            LaunchedEffect(Unit)
            {
                viewModel.Cargar(context)

                apps = withContext(Dispatchers.IO) { listarAppsInstaladas(context) }    //Se pide nada más abrir la aplicación
            }

            var enabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled(GestionUso::class.java)
            ) } //Permisos concedidos
            var pantallaActual by remember { mutableStateOf(Pantalla.Desglose) }    //Navegación entre 3 pantallas


            EarnedTimeTheme(dynamicColor = false) {
                Scaffold(
                    bottomBar = {
                        if (enabled) {
                            NavigationBar(

                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .height(94.dp)
                                    .drawBehind {   //Linea separadora
                                        drawLine(
                                            Color.Gray,
                                            Offset(0f, 0f),
                                            Offset(size.width, 0f),
                                            2f
                                        )
                                    }

                            ) {
                                Pantalla.entries.forEach { pantalla ->

                                    NavigationBarItem(
                                        selected = pantallaActual == pantalla,
                                        onClick = { pantallaActual = pantalla },
                                        icon = {
                                            Box(
                                                modifier = Modifier //Circulo, alrededor de menu seleccionado
                                                    .size(66.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (pantallaActual == pantalla) {
                                                            Color.White
                                                        } else {
                                                            Color.Transparent
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                            Icon(
                                                painter = painterResource(id = pantalla.icono),
                                                contentDescription = pantalla.name,
                                                modifier = Modifier.size(29.dp),
                                                tint = if (pantallaActual == pantalla) MaterialTheme.colorScheme.surface else Color.White,
                                            )}
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Color.Transparent
                                        ),

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
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }

            LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(objetivos, key = { it.id }) { objetivo ->

                    TarjetaObjetivo(
                        objetivo = objetivo,
                        OnEditar =
                            {
                                objetivoEditando = objetivo
                                mostrarDialogo = true
                            },
                        OnEliminar =
                            {
                                viewModel.eliminarObjetivo(objetivo.id)
                            }
                        )

                }
            }

            FloatingActionButton(
                onClick = { objetivoEditando = null; mostrarDialogo = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .dropShadow(
                        shape = CircleShape,
                        shadow = Shadow(
                            radius = 7.dp,
                            spread = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            offset = DpOffset(0.dp, 0.dp)
                        )
                    )
                    .border(2.dp, color = Color.Black, CircleShape)
                    .clip(CircleShape),
                containerColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir grupo", Modifier.size(38.dp), tint = Color.Black)
            }
        }

        if (mostrarDialogo) {
            DialogoObjetivo(
                objetivo = objetivoEditando,
                grupos = viewModel.grupos,
                onGuardar = { entry -> viewModel.guardarObjetivo(entry); mostrarDialogo = false },
                onCancelar = { mostrarDialogo = false }
            )
        }
    }


    @Composable
    fun BotonCompletar(
        bloqueado: Boolean,
        onClick: () -> Unit
    ) {

        val opacidad = if(bloqueado) 0.3f else 1f

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(enabled = !bloqueado, onClick = onClick)
                .background(Color.Gray.copy(alpha = 0.4f*opacidad))
                .drawBehind {
                    val grosor = 2.dp.toPx()
                    val topLeft = Offset(grosor / 2, grosor / 2)
                    val tam = Size(size.width - grosor, size.height - grosor)
                    // pista
                    drawArc(
                        Color.Gray.copy(alpha = opacidad) ,
                        0f,
                        360f,
                        false,
                        topLeft,
                        tam,
                        style = Stroke(grosor)
                    )

                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if(bloqueado) R.drawable.acabado else R.drawable.completar),
                contentDescription = "Marcar completado",
                tint = Color.Gray.copy(alpha = opacidad),
                modifier = Modifier.size(28.dp)
            )
        }
    }

    @Composable
    fun TarjetaObjetivo(objetivo: ObjetivoEntry, OnEditar: () -> Unit, OnEliminar: () -> Unit)
    {
        val hoy = todayKey()

        val usoDiarioHoy = if (objetivo.dateCompletado == hoy) objetivo.usoDiario else 0
        val completadoHoy = objetivo.LimiteDiario > 0 && usoDiarioHoy >= objetivo.LimiteDiario

        val usoEstaSem = if (!haPasadoUnLunesDesde(objetivo.dateCompletado)) objetivo.usoSemanal else 0
        val completadoEstaSem = objetivo.LimiteSemanal > 0 && usoEstaSem >= objetivo.LimiteSemanal

        val bloqueado = completadoHoy || completadoEstaSem

        val color = Colores[objetivo.grupo % Colores.size]

            Row(
                modifier = Modifier

                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .glow(color, 0.9f)
                    .border(1.dp, color = color, RoundedCornerShape(24.dp))
                    .padding(vertical = 8.dp, horizontal = 8.dp),

                verticalAlignment = Alignment.CenterVertically,

            ) {


                IconButton(onClick = { OnEditar() }) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.Black)
                }
                IconButton(onClick = { OnEliminar() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Black)
                }

                Column(modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)) {

                    Row()
                    {
                        Text(text = objetivo.Objetivo, fontWeight = FontWeight.Bold , color =  Color.Black)
                        Text(text = " +${objetivo.TiempoExtra / 60} min", style = MaterialTheme.typography.bodyMedium, color =  Color.Black)
                    }

                    Text(
                        text = "${usoDiarioHoy}${if(objetivo.LimiteDiario>0) "/"+ objetivo.LimiteDiario else ""} hoy · ${usoEstaSem}${if(objetivo.LimiteSemanal>0)"/"+objetivo.LimiteSemanal else ""} esta semana",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }

                BotonCompletar(
                    bloqueado = bloqueado,
                    onClick = { viewModel.completarObjetivo(objetivo) }
                )
            }

    }

    @OptIn(ExperimentalMaterial3Api::class) //Para el DatePicker
    @Composable
    fun DialogoObjetivo(
        objetivo: ObjetivoEntry?,
        grupos: List<GrupoEntry>,
        onGuardar: (ObjetivoEntry) -> Unit,
        onCancelar: () -> Unit
    ) {
        var texto by remember { mutableStateOf(objetivo?.Objetivo ?: "") }
        var minutosExtra by remember { mutableStateOf(((objetivo?.TiempoExtra ?: 300L) / 60).toString()) }
        var limiteDiario by remember { mutableStateOf((objetivo?.LimiteDiario ?: 0).toString()) }
        var limiteSemanal by remember { mutableStateOf((objetivo?.LimiteSemanal ?: 0).toString()) }
        var fechaLimite by remember { mutableStateOf(objetivo?.fechaLimite ?: "") }
        var mostrarDatePicker by remember { mutableStateOf(false) }
        var grupoId by remember { mutableStateOf(objetivo?.grupo ?: 0) }
        var selectorAbierto by remember { mutableStateOf(false) }
        val grupoSeleccionado = grupos.firstOrNull { it.id == grupoId }

        AlertDialog(
            containerColor = Color.Black,
            modifier = Modifier
                .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                .dropShadow(
                    shape = RoundedCornerShape(24.dp),
                    shadow = Shadow(
                        radius = 14.dp,
                        spread = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        offset = DpOffset(0.dp, 0.dp)
                    )
                ),
            onDismissRequest = onCancelar,
            title = { Text(if (objetivo == null) "Nuevo objetivo" else "Editar objetivo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it },
                        label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Gray,
                            focusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color.LightGray,
                            cursorColor = Color.LightGray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = minutosExtra,
                        onValueChange = { minutosExtra = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutos extra por completar") },
                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Gray,
                            focusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color.LightGray,
                            cursorColor = Color.LightGray
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox( //Selector de grupo
                        expanded = selectorAbierto,
                        onExpandedChange = { selectorAbierto = it }
                    ) {
                        OutlinedTextField(
                            value = grupoSeleccionado?.nombre ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Grupo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = selectorAbierto) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),

                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Gray,
                                focusedBorderColor = Color.LightGray,
                                unfocusedLabelColor = Color.Gray,
                                focusedLabelColor = Color.LightGray,
                                cursorColor = Color.LightGray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = selectorAbierto,
                            onDismissRequest = { selectorAbierto = false }
                        ) {
                            grupos.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g.nombre, color = lerp(Colores[g.id % Colores.size], Color.White, 0.8f)) },
                                    onClick = { grupoId = g.id; selectorAbierto = false }
                                )
                            }
                        }
                    }
                    Row {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = limiteDiario,
                            onValueChange = { limiteDiario = it.filter { c -> c.isDigit() } },
                            label = { Text("Limite diario")},
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Gray,
                                focusedBorderColor = Color.LightGray,
                                unfocusedLabelColor = Color.Gray,
                                focusedLabelColor = Color.LightGray,
                                cursorColor = Color.LightGray
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = limiteSemanal,
                            onValueChange = { limiteSemanal = it.filter { c -> c.isDigit() } },
                            label = { Text("Limite semanal") },

                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Gray,
                                focusedBorderColor = Color.LightGray,
                                unfocusedLabelColor = Color.Gray,
                                focusedLabelColor = Color.LightGray,
                                cursorColor = Color.LightGray
                            )
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
                                Text(if (fechaLimite.isBlank()) "Elegir fecha" else "Cambiar", color = Color.White)
                            }
                            if (fechaLimite.isNotBlank()) {
                                TextButton(onClick = { fechaLimite = "" }) { Text("Quitar", color = Color.White) }
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
                                fechaLimite = fechaLimite,
                                grupo = grupoId
                            )
                        )

                    },
                    enabled = grupoSeleccionado!=null) { Text("Guardar", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Color.White) } }
        )

        if (mostrarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = dateKeyToMillis(fechaLimite) ?: System.currentTimeMillis()  //Autoselección de último elegido o hoy
            )
            DatePickerDialog(
                colors = DatePickerDefaults.colors(
                    containerColor = Color.Black
                ),
                    onDismissRequest = { mostrarDatePicker = false },
                modifier = Modifier
                    .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                    .dropShadow(
                        shape = RoundedCornerShape(24.dp),
                        shadow = Shadow(
                            radius = 14.dp,
                            spread = 1.dp,
                            color = Color.White.copy(alpha = 0.4f),
                            offset = DpOffset(0.dp, 0.dp)
                        )
                    ),
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                fechaLimite = millisToDateKey(millis)
                            }
                            mostrarDatePicker = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { mostrarDatePicker = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancelar")
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        headlineContentColor = Color.White,
                        weekdayContentColor = Color.White,
                        navigationContentColor = Color.White,
                        dayContentColor = Color.White,
                        selectedDayContentColor = Color.Black,
                        selectedDayContainerColor = Color.White,
                        todayContentColor = Color.White,
                        todayDateBorderColor = Color.White,
                        yearContentColor = Color.White,
                        currentYearContentColor = Color.White,
                        selectedYearContentColor = Color.Black,
                        selectedYearContainerColor = Color.White,
                        dividerColor = Color.White
                    )
                )
            }

        }
    }

    val Colores = listOf(   //Colores de grupo, agregar más
        Color(0xFF3ac4cf),
        Color(0xFFeb6b21),
        Color(0xFF89d93f),
        Color(0xFFe82c6c)
    )


    fun Modifier.glow(color: Color, offsetX: Float): Modifier = drawBehind {        //Fondo para contenedores
        drawRect(
            Brush.radialGradient(
                colors = listOf(lerp(color, Color.LightGray, 0.65f), Color.White),
                center = Offset(offsetX * size.width, 10f),
                radius = size.width /2
            )
        )
    }


    @Composable
    fun NotebookListCard(
        modifier: Modifier = Modifier,
        color:  Color,
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
                .dropShadow(
                    shape = RoundedCornerShape(24.dp),
                    shadow = Shadow(
                        radius = 10.dp,
                        spread = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        offset = DpOffset(0.dp, 0.dp)
                    )
                )
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, color = color, RoundedCornerShape(24.dp))
                .glow(color, 0.5f)
                .padding(top = 2.dp)

        ) {
            // Cabecera
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {
                    Text(
                        text = grupo.nombre,
                        color = MaterialTheme.colorScheme.surface,
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
                            //Sirve como track de la linea de progreso real. Utilizar solo track color en la original no daba el resultado esperado
                            progress = { 0f },
                            drawStopIndicator = {}, //Para quitar el punto final
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(15.dp),

                            color = Color.White,
                            trackColor = Color.White,
                            gapSize = 0.dp,
                        )

                        LinearProgressIndicator(
                            //Track de fondo
                            progress = { progressGlobal },
                            drawStopIndicator = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(11.dp)
                                .padding(horizontal = 2.dp),

                            color = color,
                            trackColor = Color.White.copy(alpha = 0f),
                            gapSize = 0.dp,
                        )


                    }

                    Spacer(modifier = Modifier.height(5.dp))
                    //Subtitulo
                    Text(
                        text = "Usado: $TextoUso de $TextoLim",
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )

                }

            }

            // Listado
            Column(
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 6.dp)    //A la propia columna
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp),   //A lo de dentro
            ) {
                Spacer(modifier = Modifier.height(6.dp))


                Usages.sortedByDescending { it.secondsUsed } .forEachIndexed { index, usage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        val nombre = viewModel.MapaApps[usage.domain] ?: usage.domain   //Traduce el dominio de la aplicacion a esta


                        IconoApp(usage.domain, nombre)

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            modifier = Modifier.weight(1f),
                            text = nombre,
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
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
                            color = color.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
    @Composable
    fun PantallaDesglose(grupos: List<GrupoEntry>) {   //Desglose del consumo de tiempo

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 40.dp), // deja sitio a la sombra
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(grupos) {  item ->
                        NotebookListCard(
                            color = Colores[item.id % Colores.size],
                            grupo = item
                        )
                    }
                }


    }



    @Composable
    fun PantallaGrupos(grupos: List<GrupoEntry>, dominios: List<DomainEntry>, apps: List<AppEntry>) {
        var grupoEditando by remember { mutableStateOf<GrupoEntry?>(null) }
        var mostrarDialogoGrupo by remember { mutableStateOf(false) }
        var grupoAnadirId by remember { mutableStateOf<Int?>(null) }   // grupo al que se añade
        var mostrarElegirTipo by remember { mutableStateOf(false) }
        var mostrarDialogoDominio by remember { mutableStateOf(false) }
        var mostrarSelectorApps by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            if (grupos.isEmpty()) {
                Text(
                    text = "No tienes ningún grupo. Pulsa + para crear uno.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues( horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(grupos, key = { it.id }) { grupo ->
                    GrupoCard(
                        color = Colores[grupo.id % Colores.size],
                        grupo = grupo,
                        webs = dominios.filter { it.grupo == grupo.id }.sortedBy { it.domain },
                        apps = apps.filter { it.grupo == grupo.id }.sortedBy { it.appLabel.lowercase() },
                        onEditar = {
                                        grupoEditando = grupo
                                        mostrarDialogoGrupo = true
                                   },
                        onAnadir = {    grupoAnadirId = grupo.id
                                        mostrarElegirTipo = true
                                   },
                    )
                }
            }


            FloatingActionButton(
                onClick = { grupoEditando = null; mostrarDialogoGrupo = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .dropShadow(
                        shape = CircleShape,
                        shadow = Shadow(
                            radius = 7.dp,
                            spread = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            offset = DpOffset(0.dp, 0.dp)
                        )
                    )
                    .border(2.dp, color = Color.Black, CircleShape)
                    .clip(CircleShape),
                containerColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir grupo", Modifier.size(38.dp), tint = Color.Black)
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
    fun IconoApp(dominio: String, nombre: String)
    {
        var icon: Drawable? = null
        if(!dominio.equals(nombre))
        {
            icon = packageManager.getApplicationIcon(dominio)

            if(icon!=null){
                Image(
                    bitmap = toImageBitmap(icon),
                    contentDescription = dominio,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        if(dominio.equals(nombre) || icon==null){
            Spacer(modifier = Modifier.size(3.dp))
            Icon(
                painter = painterResource(R.drawable.website),
                contentDescription = dominio,
                modifier = Modifier.size(30.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.size(3.dp))
        }
    }


    @Composable
    fun FilaMiembro(dominio: String, onEliminar: () -> Unit) {

        val nombre = viewModel.MapaApps[dominio] ?: dominio

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconoApp(dominio, nombre)


            Spacer(modifier = Modifier.size(6.dp))

            Text(
                text = nombre,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onEliminar, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Quitar",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    @Composable
    fun DialogoGrupo(grupo: GrupoEntry?, onGuardar: (GrupoEntry) -> Unit, onCancelar: () -> Unit) {

        var nombre by remember { mutableStateOf(grupo?.nombre ?: "") }
        var minutos by remember { mutableStateOf(((grupo?.limite?.LimiteBaseSegs ?: 1800L) / 60).toString()) }

        AlertDialog(
            containerColor = Color.Black,
            modifier = Modifier
                .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                .dropShadow(
                    shape = RoundedCornerShape(24.dp),
                    shadow = Shadow(
                        radius = 14.dp,
                        spread = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        offset = DpOffset(0.dp, 0.dp)
                    )
                ),
            onDismissRequest = onCancelar,
            title = { Text(if (grupo == null) "Nuevo grupo" else "Editar grupo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it },
                        label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Gray,
                            focusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color.LightGray,
                            cursorColor = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = minutos,
                        onValueChange = { minutos = it.filter { c -> c.isDigit() } },
                        label = { Text("Límite diario (minutos)") },
                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Gray,
                            focusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color.LightGray,
                            cursorColor = Color.LightGray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nombre.isNotBlank(),
                    onClick = {

                    val base = (minutos.toLongOrNull() ?: 0L) * 60


                        // en caso de que ya hubiera extra de hoy
                        val extra = grupo?.let { it.limite.LimiteActualSegs } ?: base

                        onGuardar(
                            GrupoEntry(
                                id = grupo?.id ?: if(viewModel.grupos.size>0) (viewModel.grupos.sortedByDescending { it.id }.first().id+1) else 1,
                                nombre = nombre.trim(),
                                limite = Limite(
                                    LimiteBaseSegs = base,
                                    LimiteActualSegs = extra,
                                    date = grupo?.limite?.date ?: todayKey()
                                )
                            )
                        )

                }) { Text("Guardar", color = if(nombre.isNotBlank()) Color.White else Color.Gray) }
            },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Color.White) } }
        )
    }


    @Composable
    fun SelectorDeApps(
        yaAgregadas: Set<String>,
        onSeleccionar: (AppInstalada) -> Unit,
        onCancelar: () -> Unit
    ) {

        var busqueda by remember { mutableStateOf("") }

        val filtradas = apps.filter {
            it.packageName !in yaAgregadas && it.nombre.contains(busqueda, ignoreCase = true)
        }

        AlertDialog(
            containerColor = Color.Black,
            modifier = Modifier
                .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                .dropShadow(
                    shape = RoundedCornerShape(24.dp),
                    shadow = Shadow(
                        radius = 14.dp,
                        spread = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        offset = DpOffset(0.dp, 0.dp)
                    )
                ),
            onDismissRequest = onCancelar,
            title = { Text("Elegir app") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        label = { Text("Buscar") },
                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Gray,
                            focusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color.LightGray,
                            cursorColor = Color.LightGray
                        )
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
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Color.White) } }
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
            containerColor = Color.Black,
            modifier = Modifier
                .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                .dropShadow(
                    shape = RoundedCornerShape(24.dp),
                    shadow = Shadow(
                        radius = 14.dp,
                        spread = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        offset = DpOffset(0.dp, 0.dp)
                    )
                ),
            onDismissRequest = onCancelar,
            title = { Text( "Nueva Web", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        label = { Text("Web (ej: instagram.com)") },
                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Gray,
                            focusedBorderColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedLabelColor = Color.LightGray,
                            cursorColor = Color.LightGray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = texto.isNotBlank(),
                    onClick = {
                        onGuardar(DomainEntry(
                            domain = texto.trim().lowercase(), banned = baneado
                        ))

                }) { Text("Guardar", color = if(texto.isNotBlank()) Color.White else Color.Gray) }
            },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Color.White) } }
        )
    }


    @Composable
    fun PantallaSinPermiso(onAbrirAjustes: () -> Unit, onEnabled: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
            Button(onClick = onAbrirAjustes,
                colors = ButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.White,
                    disabledContainerColor = Color.White,
                    disabledContentColor = Color.White
                )
            ) { Text("Abrir ajustes de accesibilidad", color = Color.Black) }
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
            ) { Text("Comprobar de nuevo", color = Color.White) }
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
            containerColor = Color.Black,
            modifier = Modifier
                .border(2.dp, Color.White, RoundedCornerShape(24.dp))
                .dropShadow(
                    shape = RoundedCornerShape(24.dp),
                    shadow = Shadow(
                        radius = 14.dp,
                        spread = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        offset = DpOffset(0.dp, 0.dp)
                    )
                ),
            onDismissRequest = onCancelar,
            title = { Text("¿Qué quieres añadir?") },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top=24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onElegirSitio, modifier = Modifier.weight(1f)) { Text("Sitio web", color = Color.White) }
                    TextButton(onClick = onElegirApp, modifier = Modifier.weight(1f)) { Text("Aplicación", color = Color.White) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Color.White) } }
        )
    }


    @Composable
    fun GrupoCard(
        color:  Color,
        grupo: GrupoEntry,
        webs: List<DomainEntry>,
        apps: List<AppEntry>,
        onEditar: () -> Unit,
        onAnadir: () -> Unit,
    ) {

        //Las junto en un mapa con la accion de eliminacion correspondiente para pasar a Fila Miembro
        val miembros: List<Pair<String, () -> Unit>> = webs.map { web -> web.domain to { viewModel.eliminarDominio(web.domain) } } + apps.map { app -> app.packageName to { viewModel.eliminarApp(app.packageName) } }


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dropShadow(
                    shape = RoundedCornerShape(24.dp),
                    shadow = Shadow(
                        radius = 10.dp,
                        spread = 1.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        offset = DpOffset(0.dp, 0.dp)
                    )
                )
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, color = color, RoundedCornerShape(24.dp))
                .glow(color, 0.3f)
                .padding(top = 2.dp)
        ) {
            var desplegado by remember { mutableStateOf(true) }


            // Cabecera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                val rotacion by animateFloatAsState(
                    targetValue = if (desplegado) 180f else 0f,
                    label = "rotacionFlecha"
                )

                IconButton(
                    onClick = { desplegado = !desplegado }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (desplegado) "Plegar" else "Desplegar",
                        modifier = Modifier.rotate(rotacion),
                        tint = Color.DarkGray
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = grupo.nombre,
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Lim: ${formatTime(grupo.limite.LimiteBaseSegs)}",
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onEditar) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar grupo", tint = MaterialTheme.colorScheme.surface)
                }
                IconButton(onClick = {viewModel.eliminarGrupo(grupo.id)}) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar grupo", tint = MaterialTheme.colorScheme.surface)
                }
            }

            // Listado
            if(desplegado)
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = 18.dp)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    if (miembros.isEmpty()) {
                        Text(
                            text = "Sin webs ni apps todavía",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }

                    miembros.forEach { (dominio, quitar) ->
                        FilaMiembro(dominio,  quitar)
                    }

                    TextButton(onClick = onAnadir, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = color
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir web o app", color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

        }
    }

}