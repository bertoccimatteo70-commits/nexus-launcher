package com.nexus.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.common.GeoPlace
import com.nexus.core.common.NexusFormatters
import com.nexus.core.data.WeatherRepository
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusEmptyState
import com.nexus.core.designsystem.NexusGlyph
import com.nexus.core.designsystem.NexusGlyphId
import com.nexus.core.designsystem.NexusLoading
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.model.WeatherForecastDay
import com.nexus.core.model.WeatherKind
import com.nexus.core.model.WeatherReport
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun WeatherTool(repository: WeatherRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repository.refresh(force = false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Città, es. Milano") },
                leadingIcon = {
                    NexusGlyph(NexusGlyphId.SEARCH, contentDescription = null, size = 18.dp)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            NexusButton("Cerca", {
                if (query.isBlank()) {
                    searchError = "Inserisci una città."
                    return@NexusButton
                }
                searching = true
                searchError = null
                scope.launch {
                    repository.lookupCity(query)
                        .onSuccess { places ->
                            results = places
                            if (places.isEmpty()) {
                                searchError = "Nessuna città trovata per «$query»."
                            }
                        }
                        .onFailure { failure ->
                            searchError = "Ricerca non disponibile: ${failure.message ?: "errore di rete"}"
                        }
                    searching = false
                }
            }, enabled = !searching, leading = NexusGlyphId.SEARCH)
        }

        NexusButton(
            "Usa posizione approssimativa",
            {
                searching = true
                scope.launch {
                    repository.useAutomaticLocation()
                    searching = false
                }
            },
            filled = false,
            leading = NexusGlyphId.WEATHER,
            enabled = !searching,
        )

        if (searching) {
            NexusLoading("Ricerca in corso…")
        }
        searchError?.let { NexusNotice(it, tone = NexusNoticeTone.WARNING) }
        if (results.isNotEmpty()) {
            NexusPanel {
                Text(
                    "RISULTATI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                results.forEach { place ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    repository.useCity(place)
                                    results = emptyList()
                                    query = place.name
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(place.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                place.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("Usa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        state.error?.let { error ->
            NexusNotice(
                error,
                tone = if (state.report == null) NexusNoticeTone.ERROR else NexusNoticeTone.WARNING,
                actionLabel = "Riprova",
                onAction = { repository.refresh(force = true) },
            )
        }

        state.report?.let { report ->
            WeatherReportCard(
                report = report,
                loading = state.loading,
                onRefresh = { repository.refresh(force = true) },
            )
            state.forecast?.days?.takeIf { it.isNotEmpty() }?.let { days ->
                NexusPanel {
                    Text(
                        "PREVISIONI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.take(4).forEach { day ->
                            ForecastDay(day, Modifier.weight(1f))
                        }
                    }
                }
            }
        } ?: run {
            if (!state.loading) {
                NexusEmptyState(
                    title = "Meteo non disponibile",
                    message = "Nexus usa Open-Meteo senza account. Controlla la connessione o cerca una città: l'ultimo dato valido viene conservato per l'uso offline.",
                    actionLabel = "Riprova",
                    onAction = { repository.refresh(force = true) },
                )
            }
        }

        if (state.loading && state.report == null) {
            NexusLoading("Caricamento meteo…")
        }

        NexusNotice(
            "Dati meteo: Open-Meteo. Con la posizione automatica viene usata solo la posizione approssimativa; in alternativa scegli una città. Le richieste avvengono solo per aggiornare il meteo.",
            tone = NexusNoticeTone.INFO,
        )
        Text(
            if (state.report?.fromCache == true) {
                "Stai vedendo l'ultimo dato salvato sul dispositivo."
            } else if (state.city.isNotBlank()) {
                "Località: ${state.city}"
            } else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeatherReportCard(
    report: WeatherReport,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    NexusPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    report.city.ifBlank { "Posizione" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    report.kind.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NexusGlyph(
                NexusGlyphId.WEATHER,
                contentDescription = null,
                size = 38.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            NexusFormatters.temperature(report.temperatureC),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NexusTag("Percepita ${NexusFormatters.temperature(report.feelsLikeC)}")
            NexusTag("Max ${NexusFormatters.temperature(report.highC)}")
            NexusTag("Min ${NexusFormatters.temperature(report.lowC)}")
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Aggiornato ${NexusFormatters.relative(report.fetchedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (report.fromCache) {
                NexusTag("Cache offline")
                Spacer(Modifier.width(6.dp))
            }
            NexusButton(
                if (loading) "Aggiorno…" else "Aggiorna",
                onRefresh,
                filled = false,
                enabled = !loading,
                leading = NexusGlyphId.REFRESH,
            )
        }
    }
}

@Composable
private fun ForecastDay(day: WeatherForecastDay, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val date = LocalDate.ofEpochDay(day.epochDay)
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)
                .replaceFirstChar { it.titlecase(Locale.ITALIAN) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        NexusGlyph(
            NexusGlyphId.WEATHER,
            contentDescription = day.kind.label,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            NexusFormatters.temperature(day.highC),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            NexusFormatters.temperature(day.lowC),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
