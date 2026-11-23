package com.nexus.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.CalcResult
import com.nexus.core.common.Calculator
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.CalendarRepository
import com.nexus.core.data.NotesRepository
import com.nexus.core.data.SettingsRepository
import com.nexus.core.data.TimerRepository
import com.nexus.core.data.TimerScheduler
import com.nexus.core.data.WeatherRepository
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusChoiceRow
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusIconButton
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusTopBar
import com.nexus.core.designsystem.NexusToggleRow
import com.nexus.core.model.NexusSettings
import com.nexus.core.model.Note
import com.nexus.core.model.ToolDestination
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun ToolsScreen(
    initialTool: ToolDestination?,
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    notesRepository: NotesRepository,
    calendarRepository: CalendarRepository,
    weatherRepository: WeatherRepository,
    timerRepository: TimerRepository,
    timerScheduler: TimerScheduler,
    onBack: () -> Unit,
) {
    var selectedName by rememberSaveable {
        mutableStateOf((initialTool ?: ToolDestination.CALCULATOR).name)
    }
    val selected = runCatching { ToolDestination.valueOf(selectedName) }
        .getOrDefault(ToolDestination.CALCULATOR)
    LaunchedEffect(initialTool) {
        if (initialTool != null) selectedName = initialTool.name
    }
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NexusTopBar(
            title = "Strumenti Nexus",
            subtitle = selected.label,
            onBack = onBack,
        )
        NexusChoiceRow(
            options = ToolDestination.entries,
            selected = selected,
            label = { it.label },
            onSelect = { selectedName = it.name },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.weight(1f)) {
            when (selected) {
                ToolDestination.CALCULATOR -> CalculatorTool()
                ToolDestination.NOTES -> NotesTool(notesRepository)
                ToolDestination.TIMER -> TimerTool(timerRepository, timerScheduler)
                ToolDestination.CALENDAR -> CalendarTool(calendarRepository, settings)
                ToolDestination.WEATHER -> WeatherTool(weatherRepository)
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Calculator                                                          */
/* ------------------------------------------------------------------ */

private data class CalcKey(val label: String, val token: String, val accent: Boolean = false)

private val calcRows = listOf(
    listOf(
        CalcKey("C", "clear"),
        CalcKey("⌫", "back"),
        CalcKey("(", "("),
        CalcKey(")", ")"),
    ),
    listOf(
        CalcKey("7", "7"),
        CalcKey("8", "8"),
        CalcKey("9", "9"),
        CalcKey("÷", "/"),
    ),
    listOf(
        CalcKey("4", "4"),
        CalcKey("5", "5"),
        CalcKey("6", "6"),
        CalcKey("×", "*"),
    ),
    listOf(
        CalcKey("1", "1"),
        CalcKey("2", "2"),
        CalcKey("3", "3"),
        CalcKey("−", "-"),
    ),
    listOf(
        CalcKey("0", "0"),
        CalcKey(".", "."),
        CalcKey("%", "%"),
        CalcKey("+", "+"),
    ),
    listOf(
        CalcKey("^", "^"),
        CalcKey("=", "=", accent = true),
    ),
)

@Composable
private fun CalculatorTool() {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var justEvaluated by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }

    fun evaluate() {
        val current = expression.trim()
        if (current.isEmpty()) return
        when (val outcome = Calculator.evaluate(current)) {
            is CalcResult.Value -> {
                result = outcome.display
                error = null
                history = (listOf("${prettyExpression(current)} = ${outcome.display}") + history).take(6)
                justEvaluated = true
            }
            is CalcResult.Error -> {
                error = outcome.message
                result = null
            }
        }
    }

    fun press(key: CalcKey) {
        when (key.token) {
            "clear" -> {
                expression = ""
                result = null
                error = null
                justEvaluated = false
            }
            "back" -> {
                expression = expression.dropLast(1)
                justEvaluated = false
            }
            "=" -> evaluate()
            else -> {
                val token = key.token
                val isOperator = token.length == 1 && token in "+-*/%^"
                expression = when {
                    justEvaluated && isOperator -> (result ?: "") + token
                    justEvaluated -> token
                    token == "." && expression.takeLastWhile { it.isDigit() }.contains('.') -> expression
                    token in setOf("+", "*", "/", "%", "^", "-") &&
                        expression.isNotEmpty() &&
                        expression.last() in "+-*/%^" -> expression.dropLast(1) + token
                    else -> expression + token
                }
                justEvaluated = false
                result = null
                error = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NexusPanel {
            Text(
                "ESPRESSIONE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                prettyExpression(expression).ifBlank { "0" },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            val shown = result ?: error
            if (shown != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (error != null) "Errore: $shown" else "= $shown",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Supporta + − × ÷ % ^ e parentesi. La virgola decimale è accettata.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        calcRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    CalcButton(
                        key = key,
                        modifier = if (key.accent) Modifier.weight(2f) else Modifier.weight(1f),
                        onClick = { press(key) },
                    )
                }
                if (row.size == 2) {
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        if (history.isNotEmpty()) {
            NexusPanel {
                Text("STORICO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                history.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                TextButton(onClick = { history = emptyList() }) { Text("Pulisci storico") }
            }
        }
        Text(
            "Calcoli eseguiti localmente dal motore Nexus, senza servizi esterni.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalcButton(key: CalcKey, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background = if (key.accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (key.accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background.copy(alpha = if (key.accent) 1f else 0.82f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Tasto ${key.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(key.label, style = MaterialTheme.typography.titleMedium, color = content)
    }
}

private fun prettyExpression(expression: String): String = expression
    .replace("*", "×")
    .replace("/", "÷")
    .replace("-", "−")

/* ------------------------------------------------------------------ */
/* Notes                                                               */
/* ------------------------------------------------------------------ */

@Composable
private fun NotesTool(repository: NotesRepository) {
    val notes by repository.notes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    var editorTitle by remember { mutableStateOf("") }
    var editorBody by remember { mutableStateOf("") }
    var editorPinned by remember { mutableStateOf(false) }
    val filtered = remember(notes, query) {
        notes.filter {
            query.isBlank() ||
                it.title.contains(query.trim(), ignoreCase = true) ||
                it.body.contains(query.trim(), ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Cerca nelle note") },
                leadingIcon = {
                    NexusGlyph(NexusGlyphId.SEARCH, contentDescription = null, size = 18.dp)
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            NexusButton("Nuova", {
                editing = Note(
                    id = UUID.randomUUID().toString(),
                    title = "",
                    body = "",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                editorTitle = ""
                editorBody = ""
                editorPinned = false
            }, leading = NexusGlyphId.PLUS)
        }
        Text(
            "${notes.size} note cifrate con Android Keystore · salvate solo su questo dispositivo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = filtered, key = { it.id }) { note ->
                NoteCard(note = note, onClick = {
                    editing = note
                    editorTitle = note.title
                    editorBody = note.body
                    editorPinned = note.pinned
                })
            }
            if (filtered.isEmpty()) {
                item {
                    NexusEmptyState(
                        title = if (notes.isEmpty()) "Nessuna nota" else "Nessun risultato",
                        message = "Crea una nota: titolo e contenuto vengono cifrati prima di essere scritti sul disco.",
                        actionLabel = "Nuova nota",
                        onAction = {
                            editing = Note(
                                id = UUID.randomUUID().toString(),
                                title = "",
                                body = "",
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                            editorTitle = ""
                            editorBody = ""
                            editorPinned = false
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    editing?.let { note ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (notes.any { it.id == note.id }) "Modifica nota" else "Nuova nota") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = editorTitle,
                        onValueChange = { editorTitle = it },
                        label = { Text("Titolo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editorBody,
                        onValueChange = { editorBody = it },
                        label = { Text("Contenuto") },
                        minLines = 5,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NexusToggleRow(
                        title = "Fissa in alto",
                        checked = editorPinned,
                        onCheckedChange = { editorPinned = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val saved = note.copy(
                            title = editorTitle.trim().ifBlank { "Senza titolo" },
                            body = editorBody,
                            pinned = editorPinned,
                            updatedAt = now,
                        )
                        scope.launch {
                            repository.upsert(saved)
                            editing = null
                        }
                    },
                ) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    if (notes.any { it.id == note.id }) {
                        TextButton(onClick = {
                            deleting = note
                            editing = null
                        }) { Text("Elimina") }
                    }
                    TextButton(onClick = { editing = null }) { Text("Annulla") }
                }
            },
        )
    }

    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare la nota?") },
            text = { Text("«${note.title.ifBlank { "Senza titolo" }}» verrà rimossa definitivamente.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.delete(note.id)
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
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Column(
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
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.title.ifBlank { "Senza titolo" },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.pinned) {
                NexusTag("Fissa")
                Spacer(Modifier.width(6.dp))
            }
            Text(
                NexusFormatters.dateShort(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (note.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                note.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Time helpers shared by timer and calendar                           */
/* ------------------------------------------------------------------ */

internal fun epochToLocalDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
