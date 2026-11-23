package com.nexus.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.CalendarRepository
import com.nexus.core.data.TimerRepository
import com.nexus.core.data.TimerScheduler
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.model.CalendarEvent
import com.nexus.core.model.NexusSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/* ------------------------------------------------------------------ */
/* Timer                                                               */
/* ------------------------------------------------------------------ */

@Composable
internal fun TimerTool(repository: TimerRepository, scheduler: TimerScheduler) {
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    var minutesInput by rememberSaveable { mutableStateOf("5") }
    var secondsInput by rememberSaveable { mutableStateOf("0") }
    var labelInput by rememberSaveable { mutableStateOf("Timer Nexus") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snapshot?.targetAtEpochMillis) {
        if (snapshot == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            val target = snapshot?.targetAtEpochMillis ?: break
            if (now >= target) {
                repository.refresh()
                message = "Tempo scaduto: il timer è terminato."
                break
            }
            delay(1_000)
        }
    }

    val remaining = snapshot?.let { (it.targetAtEpochMillis - now).coerceAtLeast(0) } ?: 0L
    val totalSeconds = (minutesInput.toIntOrNull() ?: 0) * 60 + (secondsInput.toIntOrNull() ?: 0)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NexusPanel {
            Text(
                "TIMER NEXUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (snapshot != null) NexusFormatters.timer(remaining) else "00:00",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 56.sp,
                ),
                color = if (snapshot != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    snapshot == null -> "Nessun timer attivo"
                    else -> "${snapshot?.label.orEmpty()} · fine alle ${
                        NexusFormatters.time(snapshot?.targetAtEpochMillis ?: 0L)
                    }"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snapshot != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexusButton("Annulla timer", {
                        repository.cancel()
                        scheduler.cancel()
                        message = "Timer annullato."
                    }, filled = false)
                    NexusButton("Riavvia", {
                        val current = snapshot ?: return@NexusButton
                        val restarted = repository.start(current.durationSeconds, current.label)
                        scheduler.schedule(restarted)
                        message = "Timer riavviato."
                    }, leading = NexusGlyphId.REFRESH)
                }
            }
        }

        if (snapshot == null) {
            NexusPanel {
                Text("NUOVO TIMER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10, 25).forEach { minutes ->
                        NexusButton("$minutes min", {
                            minutesInput = minutes.toString()
                            secondsInput = "0"
                        }, filled = false)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { value -> minutesInput = value.filter { it.isDigit() }.take(3) },
                        label = { Text("Minuti") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = secondsInput,
                        onValueChange = { value -> secondsInput = value.filter { it.isDigit() }.take(2) },
                        label = { Text("Secondi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it.take(60) },
                    label = { Text("Etichetta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                NexusButton(
                    if (totalSeconds > 0) "Avvia · ${NexusFormatters.durationHuman(totalSeconds)}" else "Avvia",
                    {
                        if (totalSeconds <= 0) {
                            error = "Imposta una durata maggiore di zero."
                        } else if (totalSeconds > 24 * 60 * 60) {
                            error = "La durata massima è 24 ore."
                        } else {
                            error = null
                            val created = repository.start(totalSeconds, labelInput)
                            scheduler.schedule(created)
                            message = "Timer avviato. Riceverai un avviso allo scadere."
                        }
                    },
                    leading = NexusGlyphId.PLAY,
                    enabled = totalSeconds in 1..(24 * 60 * 60),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Il timer usa AlarmManager con setAlarmClock: su Android 12+ l'avviso può richiedere l'autorizzazione alle notifiche, ma il conteggio resta attivo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        message?.let { NexusNotice(it, tone = NexusNoticeTone.SUCCESS) }
        error?.let { NexusNotice(it, tone = NexusNoticeTone.ERROR) }

        NexusNotice(
            "Se chiudi Nexus il timer continua tramite l'allarme di sistema. Allo scadere viene mostrata una notifica locale.",
            tone = NexusNoticeTone.INFO,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Calendar                                                            */
/* ------------------------------------------------------------------ */

@Composable
internal fun CalendarTool(repository: CalendarRepository, settings: NexusSettings) {
    val events by repository.events.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var monthName by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var deleting by remember { mutableStateOf<CalendarEvent?>(null) }
    var titleInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var hourInput by remember { mutableStateOf("9") }
    var minuteInput by remember { mutableStateOf("00") }
    var durationInput by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf<String?>(null) }

    val month = runCatching { YearMonth.parse(monthName) }.getOrDefault(YearMonth.now())
    val selectedDate = runCatching { LocalDate.parse(selectedDay) }.getOrDefault(today)
    val monthEvents = remember(events, month) {
        events.filter { epochToLocalDate(it.startAt).let { date -> YearMonth.from(date) == month } }
    }
    val dayEvents = remember(events, selectedDate) {
        events.filter { epochToLocalDate(it.startAt) == selectedDate }.sortedBy { it.startAt }
    }

    fun openNew() {
        editing = CalendarEvent(
            id = UUID.randomUUID().toString(),
            title = "",
            startAt = selectedDate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            durationMinutes = 30,
        )
        titleInput = ""
        locationInput = ""
        notesInput = ""
        hourInput = "9"
        minuteInput = "00"
        durationInput = "30"
        error = null
    }

    fun openEdit(event: CalendarEvent) {
        editing = event
        titleInput = event.title
        locationInput = event.location
        notesInput = event.notes
        val local = java.time.Instant.ofEpochMilli(event.startAt)
            .atZone(ZoneId.systemDefault()).toLocalTime()
        hourInput = local.hour.toString()
        minuteInput = "%02d".format(Locale.ITALIAN, local.minute)
        durationInput = event.durationMinutes.toString()
        error = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexusButton("◀", {
                    monthName = month.minusMonths(1).toString()
                }, filled = false)
                Text(
                    NexusFormatters.monthYear(month.year, month.monthValue).replaceFirstChar { it.titlecase(Locale.ITALIAN) },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                NexusButton("▶", {
                    monthName = month.plusMonths(1).toString()
                }, filled = false)
            }
            Spacer(Modifier.height(6.dp))
            NexusButton("Oggi", {
                monthName = YearMonth.now().toString()
                selectedDay = today.toString()
            }, filled = false)
        }

        NexusPanel(padding = 10.dp) {
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "G", "V", "S", "D").forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val firstOffset = month.atDay(1).dayOfWeek.value - 1
            val totalCells = firstOffset + month.lengthOfMonth()
            val rows = (totalCells + 6) / 7
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val dayNumber = row * 7 + column - firstOffset + 1
                        if (dayNumber in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNumber)
                            val hasEvents = monthEvents.any { epochToLocalDate(it.startAt) == date }
                            val selected = date == selectedDate
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            selected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                        },
                                    )
                                    .clickable {
                                        selectedDay = date.toString()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        dayNumber.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (hasEvents) {
                                        Box(
                                            Modifier
                                                .width(4.dp)
                                                .height(4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.primary,
                                                ),
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    NexusFormatters.dayLabel(
                        selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    NexusFormatters.dateWithYear(
                        selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NexusButton("Nuovo evento", { openNew() }, leading = NexusGlyphId.PLUS)
        }

        if (dayEvents.isEmpty()) {
            NexusEmptyState(
                title = "Nessun evento",
                message = "Il calendario Nexus è locale: non richiede accesso al calendario di sistema.",
                actionLabel = "Aggiungi evento",
                onAction = { openNew() },
            )
        } else {
            dayEvents.forEach { event ->
                EventCard(
                    event = event,
                    use24Hour = settings.use24HourClock,
                    onClick = { openEdit(event) },
                    onDelete = { deleting = event },
                )
            }
        }
        Text(
            "Gli eventi sono salvati in locale e cifrati. Nexus non legge il calendario Google o di sistema.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    editing?.let { event ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (events.any { it.id == event.id }) "Modifica evento" else "Nuovo evento") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Titolo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourInput,
                            onValueChange = { hourInput = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Ora") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = minuteInput,
                            onValueChange = { minuteInput = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("Minuti") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = durationInput,
                            onValueChange = { durationInput = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("Durata min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("Luogo (facoltativo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it.take(1_000) },
                        label = { Text("Note") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { NexusNotice(it, tone = NexusNoticeTone.ERROR) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val hour = hourInput.toIntOrNull()
                    val minute = minuteInput.toIntOrNull()
                    val duration = durationInput.toIntOrNull()
                    if (titleInput.isBlank()) {
                        error = "Inserisci un titolo."
                    } else if (hour == null || hour !in 0..23 || minute == null || minute !in 0..59) {
                        error = "Orario non valido."
                    } else if (duration == null || duration !in 1..1440) {
                        error = "Durata non valida (1-1440 minuti)."
                    } else {
                        val start = LocalDateTime.of(selectedDate, LocalTime.of(hour, minute))
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val saved = event.copy(
                            title = titleInput.trim(),
                            startAt = start,
                            durationMinutes = duration,
                            location = locationInput.trim(),
                            notes = notesInput.trim(),
                        )
                        scope.launch {
                            repository.upsert(saved)
                            editing = null
                        }
                    }
                }) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    if (events.any { it.id == event.id }) {
                        TextButton(onClick = {
                            deleting = event
                            editing = null
                        }) { Text("Elimina") }
                    }
                    TextButton(onClick = { editing = null }) { Text("Annulla") }
                }
            },
        )
    }

    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare l'evento?") },
            text = { Text("«${event.title}» verrà rimosso dal calendario Nexus.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.delete(event.id)
                        deleting = null
                    }
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun EventCard(
    event: CalendarEvent,
    use24Hour: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(62.dp)) {
            Text(
                NexusFormatters.time(event.startAt, use24Hour),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${event.durationMinutes} min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.location.isNotBlank()) {
                Text(
                    event.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(onClick = onDelete)
                .padding(8.dp),
        ) {
            NexusGlyph(
                NexusGlyphId.TRASH,
                contentDescription = "Elimina evento ${event.title}",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
