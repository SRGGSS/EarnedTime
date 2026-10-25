package com.example.earnedtime

import android.Manifest
import kotlinx.coroutines.launch
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
import android.app.AlarmManager
import android.app.AppOpsManager
import android.os.Process
import android.app.PendingIntent
import androidx.activity.viewModels
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.commandiron.wheel_picker_compose.WheelTimePicker
import com.commandiron.wheel_picker_compose.core.WheelPickerDefaults
import com.example.earnedtime.data.AppEntry
import com.example.earnedtime.data.DomainEntry
import com.example.earnedtime.data.GrupoEntry
import com.example.earnedtime.data.Limite
import com.example.earnedtime.data.ObjetivoEntry
import com.example.earnedtime.data.PausaEntry
import com.example.earnedtime.data.UsageEntry
import com.example.earnedtime.ui.theme.EarnedTimeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.JdkConstants
import java.time.LocalTime
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit


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

    val viewModel: MainViewModel by viewModels()


    enum class Pantalla(val icono: Int) {
        Sitios( R.drawable.ajustes),
        Home( R.drawable.casa),
        Objetivos( R.drawable.tareas)

    }

    var apps by mutableStateOf<List<AppInstalada>>(emptyList())

    var mostrarPausa by  mutableStateOf(false)



    override fun onCreate(savedInstanceState: Bundle?) {

        val context = this
        super.onCreate(savedInstanceState)



        setContent {

            LaunchedEffect(Unit)
            {
                viewModel.Cargar()

                apps = withContext(Dispatchers.IO) { listarAppsInstaladas(context) }    //Se pide nada más abrir la aplicación
            }

            var enabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled(GestionUso::class.java)
            ) } //Permisos concedidos
            var pantallaActual by remember { mutableStateOf(Pantalla.Home) }    //Navegación entre 3 pantallas


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
                                    Pantalla.Home -> if(mostrarPausa) PantallaPausa() else PantallaDesglose(viewModel.grupos)
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
                .background(Color.Gray.copy(alpha = 0.4f * opacidad))
                .drawBehind {
                    val grosor = 2.dp.toPx()
                    val topLeft = Offset(grosor / 2, grosor / 2)
                    val tam = Size(size.width - grosor, size.height - grosor)
                    // pista
                    drawArc(
                        Color.Gray.copy(alpha = opacidad),
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
        val hoy = dayKey(0)

        val usoDiarioHoy = if (objetivo.dateCompletado == hoy) objetivo.usoDiario else 0
        val completadoHoy = objetivo.LimiteDiario > 0 && usoDiarioHoy >= objetivo.LimiteDiario

        val usoEstaSem = if (!haPasadoUnLunesDesde(objetivo.dateCompletado)) objetivo.usoSemanal else 0
        val completadoEstaSem = objetivo.LimiteSemanal > 0 && usoEstaSem >= objetivo.LimiteSemanal

        val bloqueado = completadoHoy || completadoEstaSem

        val color = ColorGrupos(objetivo.grupo)

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
                                    text = { Text(g.nombre, color = lerp(ColorGrupos(g.id), Color.White, 0.8f)) },
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
        haceDias: Int,
        grupo: GrupoEntry
    ) {

        var Usages by remember { mutableStateOf<List<UsageEntry>>(emptyList()) }

        LifecycleResumeEffect(grupo.id, haceDias) {   //Para que se actualice cada vez que se reable
            val job = CoroutineScope(Dispatchers.IO).launch {
                val result = viewModel.usosDeGrupo(grupo.id)
                withContext(Dispatchers.Main) {
                    Usages = result.filter { it.date == dayKey(haceDias) }
                }
            }

            onPauseOrDispose {
                job.cancel()
            }
        }

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
    
    fun ColorGrupos(id: Int): Color //Devuelve color perteneciente a grupo en base a su id
    {
        return Colores[id % Colores.size]
    }


    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun SelectorGrupos(     //Selector de grupos, scrolleable horizontalmente
        seleccionados: List<Int>,
        onCambio: (List<Int>) -> Unit
    ) {
        val grupos = viewModel.grupos

        if (grupos.isEmpty()) {
            Text("Crea primero un grupo", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            return
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(grupos) { g ->
                val color = ColorGrupos(g.id)
                val elegido = g.id in seleccionados

                Surface(
                    onClick = {
                        onCambio(   //Se selecciona o deselecciona
                            if (elegido) seleccionados - g.id
                            else seleccionados + g.id
                        )
                    },
                    shape = RoundedCornerShape(24),
                    color = if (elegido) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, lerp(color, Color.White, 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (elegido) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "elegido",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                        }

                        Text(
                            text = g.nombre,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }


    fun getUsageStatsForDay(context: Context, haceDias: Int): Long {        ////https://medium.com/@quiro91/show-app-usage-with-usagestatsmanager-d47294537dab
        val usm = context.getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -haceDias)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val inicio = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val fin = cal.timeInMillis

        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, inicio, fin).filter { it.firstTimeStamp in inicio until fin }.sumOf { it.totalTimeInForeground } / 1000
    }


    @Composable
    fun PantallaDesglose(grupos: List<GrupoEntry>) {   //Desglose del consumo de tiempo

        var haceDias by remember {mutableStateOf(0)}        //Los datos de, hace cuantos dias se quiere ver?
        val context = LocalContext.current

        Column(modifier = Modifier.fillMaxSize()) {
            SelectorDia(
                onAnterior = { valor -> haceDias = valor },
                onSiguiente = { valor -> haceDias = valor })

            var usoControlado by remember { mutableLongStateOf(0L) }
            var usoTotal by remember { mutableLongStateOf(0L) }
            var difControlado by remember { mutableIntStateOf(0) }    //Diferencia (percentual) con ayer
            var difTotal by remember { mutableIntStateOf(0) } //Diferencia (percentual) con ayer

            LifecycleResumeEffect(haceDias) {
                val job = CoroutineScope(Dispatchers.IO).launch {
                    val result = viewModel.getAllUsages(dayKey(haceDias))
                    val resultAyer = viewModel.getAllUsages(dayKey(haceDias+1))

                    withContext(Dispatchers.Main) {
                        usoControlado = result.sumOf { it.secondsUsed }

                        val usoAyer = resultAyer.sumOf { it.secondsUsed }
                        if(usoAyer>1)
                        {
                            difControlado = (100.0 * ((usoControlado.toDouble()/usoAyer.toDouble()) - 1.0)).toInt()
                        }
                        else{
                            difControlado = 0
                        }
                    }

                    usoTotal = getUsageStatsForDay(context, haceDias)
                    val usoTotalAyer = getUsageStatsForDay(context, haceDias +1)
                    if(usoTotalAyer>1)
                    {
                        difTotal = (100.0 * ((usoTotal.toDouble()/usoTotalAyer.toDouble())-1.0)).toInt()
                    }
                    else{
                        difTotal = 0
                    }

                }

                onPauseOrDispose {
                    job.cancel()
                }
            }

            Column (
                modifier = Modifier.fillMaxWidth()
            )
            {
                var textoControlado = buildAnnotatedString {    //Para distintos colores en texto
                    append("Uso controlado: ${formatTime(usoControlado)}")

                    if (difControlado == 0) {
                        append(" (0%)")
                    } else {
                        if (difControlado > 0) {
                            append(" (")
                            withStyle(SpanStyle(color = Color.Red))
                            {
                                append("▲")
                            }
                            append("${abs(difControlado)}%)")
                        } else {
                            append(" (")
                            withStyle(SpanStyle(color = Color.Green))
                            {
                                append("▼")
                            }
                            append("${abs(difControlado)}%)")
                        }
                    }
                }
                var textoTotal = buildAnnotatedString {    //Para distintos colores en texto
                    append("Uso total: ${formatTime(max(usoTotal, usoControlado))}")
                    if (difTotal == 0) {
                        append(" (0%)")
                    } else {
                        if (difTotal > 0) {
                            append(" (")
                            withStyle(SpanStyle(color = lerp(Color.Red,Color.Black, 0.2f)))
                            {
                                append("▲")
                            }
                            append("${abs(difTotal)}%)")
                        } else {
                            append(" (")
                            withStyle(SpanStyle(color = lerp(Color.Green,Color.Black, 0.2f)))
                            {
                                append("▼")
                            }
                            append("${abs(difTotal)}%)")
                        }
                    }
                }

                Text(textoControlado,modifier = Modifier.fillMaxWidth(), color = Color.LightGray, textAlign = TextAlign.Center)
                Spacer(Modifier.size(8.dp))
                Text(textoTotal,modifier = Modifier.fillMaxWidth(), color = Color.Gray, textAlign = TextAlign.Center)
            }

            Box(modifier = Modifier.fillMaxSize()) {

                if(usoControlado>0) //Si no se ha usado ni un segundo de los grupos controlados
                {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 32.dp,
                            vertical = 40.dp
                        ), // deja sitio a la sombra
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(grupos) { item ->
                            NotebookListCard(
                                color = Colores[item.id % Colores.size],
                                grupo = item,
                                haceDias = haceDias
                            )
                        }
                    }
                }
                else
                {
                    Text(
                        text = "No hay uso registrado este día.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                FloatingActionButton(   //Botón para acceder al menú de pausa
                    onClick = { mostrarPausa = true },
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
                    Icon(
                        painterResource(R.drawable.reloj),
                        contentDescription = "Añadir grupo",
                        Modifier.size(38.dp),
                        tint = Color.Black
                    )
                }
            }
        }

    }

    @Composable
    fun SelectorDia(
        onAnterior: (Int) -> Unit,
        onSiguiente: (Int) -> Unit
    ) {
        var haceDias by remember {mutableStateOf(0)}

        var texto = when(haceDias){
                0 -> "Hoy"
                1 -> "Ayer"
                else -> dayKey(haceDias)
        }


        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = {onAnterior(++haceDias)}, enabled = (haceDias<9)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "anterior")
            }
            Text(
                text = texto,
                modifier = Modifier.widthIn(min = 120.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            IconButton(onClick = {onSiguiente(--haceDias)}, enabled = (haceDias>0)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "siguiente")
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PantallaPausa() {

        val context = LocalContext.current

        var MenuProgramar by remember {mutableStateOf(false)}

        // Default con 00:00
        var timePickerState by remember { mutableStateOf(LocalTime.MIDNIGHT) }


        var Notificar by remember {mutableStateOf(true)}

        var pausaSeleccionada by remember {
            mutableStateOf<PausaEntry?>(null)
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                    Row(modifier = Modifier.fillMaxWidth())
                    {
                        IconButton(     //Atrás
                            modifier = Modifier
                                .size(36.dp),
                            onClick = { mostrarPausa = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Atrás",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = if(MenuProgramar) "Programar" else "Pausar",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )

                        FilledIconButton(   //Cambiar entre menús pausa momentanea y pausa programada
                            onClick = { MenuProgramar = !MenuProgramar },
                            modifier = Modifier
                                .size(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White)
                        ) {
                            Icon(
                                if(!MenuProgramar) Icons.Default.DateRange else Icons.Default.Lock,
                                contentDescription = "Programar",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                    }



                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.weight(1f)) {

                    var pausaEspontanea by remember {
                        mutableStateOf<PausaEntry?>(null)
                    }

                    LaunchedEffect(Unit) {
                        pausaEspontanea = viewModel.pausasEspontaneas()
                    }




                    if (MenuProgramar) {       //Menú programación pausas, se repiten cada semana

                        var gruposElegidos by remember { mutableStateOf<List<Int>>(emptyList()) }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {

                            AgendaSemanal(
                                pausas = viewModel.pausas.filter { it.programada },
                                onPulsar = { pausa -> pausaSeleccionada = pausa}
                            )


                            //Selector dias de la semana
                            var diasElegidos by remember { mutableStateOf(setOf<Int>()) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    space = 8.dp,
                                    alignment = Alignment.CenterHorizontally
                                ),
                            )
                            {
                                val dias = listOf("L", "M", "X", "J", "V", "S", "D")

                                for (n in 0..dias.size - 1) {
                                    val dia = n + 1
                                    Box(contentAlignment = Alignment.Center)
                                    {
                                        Surface(
                                            onClick = {
                                                if(diasElegidos.contains(dia)) diasElegidos -= dia
                                                else diasElegidos += dia
                                                      },
                                            modifier = Modifier.size(34.dp),
                                            shape = CircleShape,
                                            color = if (diasElegidos.contains(dia)) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                            border = BorderStroke(1.dp, Color.White)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dias[n],
                                                    color = Color.White
                                                )
                                            }
                                        }

                                    }

                                }
                            }


                            //Selector hora inicio y duración (2 WheelTimePickers)
                            var inicio by remember { mutableStateOf(LocalTime.of(12, 0)) }
                            var duracion by remember { mutableStateOf(LocalTime.MIDNIGHT) }

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val ancho = maxWidth / 2 - 8.dp
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Inicio", color = Color.Gray, fontSize = 13.sp)
                                        WheelTimePicker(
                                            startTime = inicio,
                                            textColor = Color.White,
                                            textStyle = TextStyle(fontSize = 18.sp),
                                            selectorProperties = WheelPickerDefaults.selectorProperties(
                                                enabled = true,
                                                shape = RoundedCornerShape(24.dp),
                                                color = Color.Transparent,
                                                border = BorderStroke(1.dp, Color.Transparent)
                                            ),
                                            size = DpSize(ancho, 90.dp)
                                        ) { inicio = it }
                                    }

                                    Spacer(Modifier.weight(1f))

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Duración", color = Color.Gray, fontSize = 13.sp)
                                        WheelTimePicker(
                                            startTime = duracion,
                                            textColor = Color.White,
                                            textStyle = TextStyle(fontSize = 18.sp),
                                            selectorProperties = WheelPickerDefaults.selectorProperties(
                                                enabled = true,
                                                shape = RoundedCornerShape(24.dp),
                                                color = Color.Transparent,
                                                border = BorderStroke(1.dp, Color.Transparent)
                                            ),
                                            size = DpSize(ancho, 90.dp)
                                        ) { duracion = it }
                                    }
                                }
                            }


                            SelectorGrupos(seleccionados = gruposElegidos, onCambio = { gruposElegidos = it })


                            Row(modifier = Modifier.fillMaxWidth())
                            {
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    enabled = diasElegidos.size > 0 && gruposElegidos.isNotEmpty() && (duracion.hour > 0 || duracion.minute > 0),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                        disabledContainerColor = Color.Gray,
                                        disabledContentColor = Color.DarkGray
                                    ),
                                    onClick = {

                                        viewModel.nuevaPausa(
                                            PausaEntry(
                                                id = UUID.randomUUID().toString(),
                                                grupos = gruposElegidos,
                                                duracionMins = duracion.hour * 60 + duracion.minute,
                                                programada = true,
                                                diasSem = diasElegidos.sorted(),      // 1 = lunes ... 7 = domingo
                                                progInicioMins = inicio.hour * 60 + inicio.minute
                                            )
                                        )

                                        diasElegidos = emptySet()  // limpia el formulario
                                        gruposElegidos = emptyList()
                                    }
                                ) { Text("Agregar", fontWeight = FontWeight.Bold) }
                            }


                        }
                        if(pausaSeleccionada != null)
                        {
                            DialogoPausaProgramada(pausaSeleccionada!!, {pausaSeleccionada = null}, OnEliminar = {viewModel.eliminarPausa(pausaSeleccionada!!.id); pausaSeleccionada = null})
                        }
                    }








                    else    //Pausa Momentanea
                    {
                        if (pausaEspontanea != null) {  //Pausa Momentanea puesta, countdown
                            var textoReloj by remember { mutableStateOf("") }
                            var pausaAhora = pausaEspontanea!!
                            var menuCancelar by remember { mutableStateOf(false) }
                            val finMillis = pausaAhora.inicioMillis + pausaAhora.duracionMins * 60_000L
                            var progreso by remember { mutableFloatStateOf(((System.currentTimeMillis() - pausaAhora!!.inicioMillis).toDouble() / (pausaAhora.duracionMins * 60_000L)).toFloat()) }


                            DisposableEffect(pausaAhora.id) {
                                val restante = (finMillis - System.currentTimeMillis()).coerceAtLeast(1L)
                                val timer = object : CountDownTimer(restante + 1000, 1000) {    //https://stackoverflow.com/questions/54095875/how-to-create-a-simple-countdown-timer-in-kotlin
                                    override fun onTick(millisUntilFinished: Long) {
                                        progreso = ((System.currentTimeMillis() - pausaAhora.inicioMillis).toDouble() / (pausaAhora.duracionMins * 60_000L)).toFloat()
                                        val Quedan = (finMillis - System.currentTimeMillis()).milliseconds
                                        textoReloj = Quedan.toComponents { hrs, min, sec, nanos -> String.format("%d:%02d:%02d", hrs, min, sec)} //https://www.baeldung.com/kotlin/duration-class-tutorial, mejor que Locale.getDefault, funciona independientemente de franja horaria
                                    }

                                    override fun onFinish() {
                                        pausaEspontanea = viewModel.pausasEspontaneas()
                                    }
                                }
                                timer.start()
                                onDispose { timer.cancel() }
                            }



                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                            {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                )
                                {
                                    CircularProgressIndicator(  //Cirucumferencia, muestra cuanto queda de la pausa
                                        progress = { 1f - progreso },
                                        Modifier
                                            .size(180.dp, 180.dp)
                                            .scale(scaleX = -1f, scaleY = 1f),
                                        color = Color.White,
                                        gapSize = 0.dp,
                                        strokeWidth = 14.dp,
                                        trackColor = Color.White.copy(alpha = 0.15f)
                                    )

                                    Text(
                                        text = textoReloj,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                }

                                Spacer(modifier = Modifier.weight(1f))

                                LazyRow(    //Muestra todos los grupos de la pausa, reutilizado más adelante, considerar si lo meto en un Composable
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(viewModel.gruposDePausa(pausaAhora.id)) { g ->
                                        val color = ColorGrupos(g.id)

                                        Surface(
                                            shape = RoundedCornerShape(24),
                                            color = Color.White.copy(alpha = 0.1f),
                                            border = BorderStroke(
                                                1.dp,
                                                lerp(color, Color.White, 0.25f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = 8.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = g.nombre,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                    }
                                }

                                Spacer(modifier = Modifier.weight(0.5f))

                                Row(modifier = Modifier.fillMaxWidth())
                                {
                                    Spacer(modifier = Modifier.weight(1f))

                                    Button(
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        colors = ButtonColors(
                                            containerColor = Color(0xFFde1212),
                                            contentColor = Color(0xFFde1212),
                                            disabledContainerColor = Color.Gray,
                                            disabledContentColor = Color.Gray
                                        ),
                                        onClick = {
                                            menuCancelar = true
                                        }
                                    ) { Text("Cancelar", color = Color.White) }
                                }


                            }

                            if(menuCancelar)
                            {
                                DialogoCancelarPausa(
                                    onCancelar =
                                        {
                                            menuCancelar=false
                                        },
                                    onCancelada =
                                        {
                                            viewModel.eliminarPausa(pausaAhora.id)
                                            pausaEspontanea = viewModel.pausasEspontaneas()
                                            pausaEspontanea = null
                                            menuCancelar=false



                                            //Eliminar notificación, por si había una pendiente https://developer.android.com/develop/background-work/services/alarms?hl=es-419&utm_source=android-studio-app&utm_medium=app
                                            val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

                                            val intent = Intent(context, AlarmaReceiver::class.java)

                                            val pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

                                            if (pendingIntent != null) {
                                                alarmManager.cancel(pendingIntent)
                                            }

                                        }
                                )
                            }

                        }








                        else {        //Menú crear pausa momentanea

                            var gruposElegidos by remember { mutableStateOf<List<Int>>(emptyList()) }

                            Spacer(modifier = Modifier.weight(1f))

                            BoxWithConstraints(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                WheelTimePicker(
                                    startTime = timePickerState,
                                    textColor = Color.White,
                                    textStyle = TextStyle(fontSize = 26.sp),
                                    selectorProperties = WheelPickerDefaults.selectorProperties(
                                        enabled = true,
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.dp, Color.Transparent)
                                    ),
                                    size = DpSize(maxWidth * (3f / 4f), 200.dp),
                                ) { snappedTime -> timePickerState = snappedTime }

                            }

                            Spacer(modifier = Modifier.weight(1f))


                            SelectorGrupos(seleccionados = gruposElegidos, onCambio = { gruposElegidos = it })

                            Spacer(modifier = Modifier.weight(1f))


                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                Arrangement.SpaceBetween,

                            )
                            {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = Notificar,
                                        onCheckedChange = { Notificar = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color.White,
                                            checkmarkColor = Color.Black,
                                            uncheckedColor = Color.White,

                                            ),

                                        )

                                    Text(
                                        text = "Enviar notificación",
                                        color = Color.White
                                    )
                                }

                                var enabled = gruposElegidos.isNotEmpty() && (timePickerState.minute>0 || timePickerState.hour>0)

                                Button(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    colors = ButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.White,
                                        disabledContainerColor = Color.Gray,
                                        disabledContentColor = Color.Gray
                                    ),
                                    enabled = enabled,
                                    onClick = {

                                        val pausa = PausaEntry(
                                            id = UUID.randomUUID().toString(),
                                            grupos = gruposElegidos,
                                            duracionMins = timePickerState.hour * 60 + timePickerState.minute,
                                            inicioMillis = System.currentTimeMillis()
                                        )
                                        viewModel.nuevaPausa(pausa)

                                        pausaEspontanea = pausa

                                        if(Notificar)
                                        {
                                            val tiempoEnSegs = (timePickerState.hour * 60 + timePickerState.minute) * 60
                                            val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager //https://developer.android.com/develop/background-work/services/alarms?hl=es-419

                                            val intent = Intent(context, AlarmaReceiver::class.java).apply {
                                                putExtra("titulo", "Fin de la pausa")
                                                putExtra("texto", "La pausa de ${formatTime(tiempoEnSegs.toLong())} ha finalizado!")
                                            }

                                            val pendingIntent = PendingIntent.getBroadcast(
                                                context,
                                                1,
                                                intent,
                                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                            )


                                            alarmManager?.set(
                                                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                                SystemClock.elapsedRealtime() + tiempoEnSegs * 1000,
                                                pendingIntent
                                            )

                                        }

                                    }
                                ) { Text("Aceptar", color = Color.Black, fontWeight = FontWeight.Bold) }

                            }

                        }
                    }



                }

            }
        }
    }

    @Composable
    fun AgendaSemanal(pausas: List<PausaEntry>, onPulsar: (PausaEntry) -> Unit) {
        val dias = listOf("L", "M", "X", "J", "V", "S", "D")
        val alturaHora = 10.dp


        val porDia = remember(pausas) {
            val mapa = (1..7).associateWith { mutableListOf<Triple<PausaEntry, Int, Int>>() }
            pausas.forEach { p ->
                p.diasSem.forEach { d ->
                    val ini = p.progInicioMins
                    val fin = ini + p.duracionMins
                    if (fin <= 1440) mapa[d]!!.add(Triple(p, ini, fin))
                    else {
                        mapa[d]!!.add(Triple(p, ini, 1440))
                        mapa[d % 7 + 1]!!.add(Triple(p, 0, fin - 1440))
                    }
                }
            }
            mapa
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // Cabecera con los días
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(28.dp))
                dias.forEach {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = it,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Columna de horas
            Row(modifier = Modifier.fillMaxWidth().height(alturaHora * 24)) {
                Box(modifier = Modifier.width(18.dp).fillMaxHeight()) {
                    listOf(0, 6, 12, 18).forEach { h ->
                        Text(
                            modifier = Modifier.offset( 0.dp, alturaHora * h - 10.dp),
                            text = "%02d".format(h),
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Columnas de los 7 días
                Row(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {   // líneas cada 6 h
                            listOf(6, 12, 18).forEach { h ->
                                drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height * h/ 24f), Offset(size.width, size.height * h/ 24f), 1f)
                            }
                        }
                ) {
                    for (d in 1..7) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            porDia[d]!!.forEach { (p, ini, fin) ->
                                val g = viewModel.grupos.firstOrNull { it.id in p.grupos }

                                if(g==null)
                                {
                                    return@forEach
                                }

                                val color = ColorGrupos(g.id )

                                Box(
                                    modifier = Modifier

                                        .offset(y = alturaHora * (ini / 60f))
                                        .border(1.dp, color, RoundedCornerShape(4.dp))
                                        .height(alturaHora * ((fin - ini) / 60f))
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(lerp(color, Color.White, 0.8f))
                                        .clickable { onPulsar(p) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.size(18.dp))
            }
        }
    }

    @Composable
    fun DialogoPausaProgramada(pausa: PausaEntry, onCancelar: () -> Unit, OnEliminar: () -> Unit)  {

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
            title = {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Pausa Programada",
                                modifier = Modifier.align(Alignment.Center),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { OnEliminar() },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = Color.White
                                )
                            }
                        }
                    },
            text = {

                val dias = listOf("L", "M", "X", "J", "V", "S", "D")

                val ini = pausa.progInicioMins
                val fin = (ini + pausa.duracionMins) % 1440

                val horario = "%02d:%02d - %02d:%02d".format(ini / 60, ini % 60, fin / 60, fin % 60)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                )
                {

                    //Visualizador grupos, reutilizado, tengo que crear Composable
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.gruposDePausa(pausa.id)) { g ->
                            val color = ColorGrupos(g.id)

                            Surface(
                                shape = RoundedCornerShape(24),
                                color = Color.White.copy(alpha = 0.1f),
                                border = BorderStroke(
                                    1.dp,
                                    lerp(color, Color.White, 0.25f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = g.nombre,
                                        color = Color.White
                                    )
                                }
                            }

                        }
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        dias.forEachIndexed { n, letra ->
                            val activo = (n + 1) in pausa.diasSem   // +1 porque se guardan de 0 a 6
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = CircleShape,
                                color = if (activo) Color.White else Color.Transparent,
                                border = BorderStroke(1.dp,  if (activo) Color.Black else Color.Gray)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letra,
                                        fontSize = 13.sp,
                                        color = if (activo) Color.Black else Color.Gray)
                                }
                            }
                        }
                    }

                    // Horas
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = horario,
                        color = Color.White,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "Duración: ${pausa.duracionMins / 60}h ${pausa.duracionMins % 60}min",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

            },
            confirmButton = { },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Color.White) } }
        )
    }


    @Composable
    fun DialogoCancelarPausa(onCancelar: () -> Unit, onCancelada: () -> Unit) {

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
            title = { Text("Cancelar Pausa") },
            text = {

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally)
                {
                    Spacer(modifier = Modifier.size(22.dp))
                    BotónCancelar("Mantener",onProgressComplete = {onCancelada()})
                }

            },
            confirmButton = { },
            dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Color.White) } }
        )
    }

    @Composable
    fun BotónCancelar(  //Mantener durante x segundos, animación de: https://proandroiddev.com/creating-an-engaging-progress-button-in-jetpack-compose-29ff8d5e383c
        text: String,
        progressDurationMillis: Float = 5f,
        onProgressComplete: () -> Unit = {}
    ) {

        var progress by remember {mutableFloatStateOf(0f)}



        val density = LocalDensity.current
        val layoutDirection = LayoutDirection.Ltr


        val defaultButtonClipShape = CircleShape


        Button(
            onClick = {},
            modifier = Modifier
                .height(IntrinsicSize.Max)
                .border(1.dp, Color.White, shape = RoundedCornerShape(24.dp))
                .onTouchHeld({ valor -> progress = valor.coerceIn(0f, 1f)}, progressDurationMillis, {onProgressComplete()})
                .drawBehind {

                    val outline = defaultButtonClipShape.createOutline(
                        this.size,
                        layoutDirection,
                        density
                    )
                    val buttonShapePath = Path().apply { addOutline(outline) }


                    clipPath(buttonShapePath) {
                        drawRect(
                            color = lerp(Color(0xFF7d0a0a), Color(0xFFde1212), progress),
                            size = Size(
                                width = this.size.width * progress,
                                height = this.size.height
                            )
                        )
                    }
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.Transparent
            ),
            shape = CircleShape
        ) {
            Text(
                text = text,
                color = Color.White
            )
        }
    }


    fun Modifier.onTouchHeld(       //https://proandroiddev.com/creating-touch-held-down-modifer-with-jetpack-compose-a01081f51edf
        onTouchHeld: (timeElapsed: Float) -> Unit,
        duracionMaxima : Float,
        onFinished: () -> Unit
    ) = composed {
        val scope = rememberCoroutineScope()
        pointerInput(onTouchHeld) {
            awaitEachGesture {
                val initialDown = awaitFirstDown(requireUnconsumed = false)
                val initialDownTime = System.nanoTime()
                val initialTouchHeldJob = scope.launch {
                    while (initialDown.pressed) {
                        val timeElapsed = System.nanoTime() - initialDownTime
                        val timeEl = timeElapsed.nanoseconds.toLong(DurationUnit.MILLISECONDS) / (duracionMaxima * 1000f)
                        onTouchHeld(timeEl)   //Modificación: Devuelve de 0 a 1 el progreso cada 0,1 segundos
                        delay(timeMillis = 25)
                        if(timeEl>=1)
                        {
                            onFinished()
                        }
                    }

                }
                waitForUpOrCancellation()
                onTouchHeld(0f)
                initialTouchHeldJob.cancel()
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
                                    date = grupo?.limite?.date ?: dayKey(0)
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

    private fun checkUsageStatsPermission() : Boolean {     //https://proandroiddev.com/accessing-app-usage-history-in-android-79c3af861ccf Para pedir permiso PACKAGE_USAGE_STATS
        val appOpsManager = getSystemService(AppCompatActivity.APP_OPS_SERVICE) as AppOpsManager
        // `AppOpsManager.checkOpNoThrow` is deprecated from Android Q
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                "android:get_usage_stats",
                Process.myUid(), packageName
            )
        }
        else {
            appOpsManager.checkOpNoThrow(
                "android:get_usage_stats",
                Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
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
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissionsResult -> permissionsResult.forEach { (permission, isGranted) ->
                    if (isGranted) {

                    } else {

                    }
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
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        {
                            var Permisos = arrayOf<String>()

                            if(ContextCompat.checkSelfPermission(context,Manifest.permission.SCHEDULE_EXACT_ALARM) != PackageManager.PERMISSION_GRANTED)
                            {
                                Permisos += Manifest.permission.SCHEDULE_EXACT_ALARM
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                Permisos += Manifest.permission.POST_NOTIFICATIONS
                            }

                            if(Permisos.isNotEmpty())
                            {
                                permissionLauncher.launch(Permisos)
                            }
                        }

                        if( !checkUsageStatsPermission() )  //El permiso para acceder al uso total no está otorgado
                        {
                            Intent( Settings.ACTION_USAGE_ACCESS_SETTINGS ).apply {
                                startActivity( this )
                            }
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
                        .padding(top = 24.dp),
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