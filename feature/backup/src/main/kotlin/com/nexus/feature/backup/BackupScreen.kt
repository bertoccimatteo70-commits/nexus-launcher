package com.nexus.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.core.common.BackupCodec
import com.nexus.core.common.NexusFormatters
import com.nexus.core.common.TransferProtocol
import com.nexus.core.data.BackupRepository
import com.nexus.core.data.BackupScheduler
import com.nexus.core.data.SettingsRepository
import com.nexus.core.designsystem.NexusButton
import com.nexus.core.designsystem.NexusDetailScreen
import com.nexus.core.designsystem.NexusDivider
import com.nexus.core.designsystem.NexusNotice
import com.nexus.core.designsystem.NexusNoticeTone
import com.nexus.core.designsystem.NexusPanel
import com.nexus.core.designsystem.NexusSectionHeader
import com.nexus.core.designsystem.NexusSettingRow
import com.nexus.core.designsystem.NexusTag
import com.nexus.core.designsystem.NexusToggleRow
import com.nexus.core.model.AppBuildInfo
import com.nexus.core.model.BackupPayload
import com.nexus.core.model.BackupPreview
import com.nexus.core.model.NEXUS_SCHEMA_VERSION
import com.nexus.core.model.NexusSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Locale

private const val DEFAULT_PORT = 47831

private data class SenderUi(
    val ip: String,
    val port: Int,
    val code: String,
    val status: String,
    val error: String? = null,
)

@Composable
fun BackupScreen(
    settings: NexusSettings,
    settingsRepository: SettingsRepository,
    backupRepository: BackupRepository,
    buildInfo: AppBuildInfo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheduler = remember(context) { BackupScheduler(context) }
    var autoFiles by remember { mutableStateOf(backupRepository.autoBackupFiles()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingPayload by remember { mutableStateOf<BackupPayload?>(null) }
    var pendingPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var pendingSource by remember { mutableStateOf("") }

    var exportDialog by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var exportConfirm by remember { mutableStateOf("") }
    var exportError by remember { mutableStateOf<String?>(null) }
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    var importDialog by remember { mutableStateOf(false) }
    var importPass by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importName by remember { mutableStateOf("") }

    var sender by remember { mutableStateOf<SenderUi?>(null) }
    var senderServer by remember { mutableStateOf<ServerSocket?>(null) }
    var senderJob by remember { mutableStateOf<Job?>(null) }
    var senderCancelled by remember { mutableStateOf(false) }
    var senderPortInput by rememberSaveable { mutableStateOf(DEFAULT_PORT.toString()) }

    var receiverHost by rememberSaveable { mutableStateOf("") }
    var receiverPort by rememberSaveable { mutableStateOf(DEFAULT_PORT.toString()) }
    var receiverCode by rememberSaveable { mutableStateOf("") }
    var receiving by remember { mutableStateOf(false) }
    var receiveError by remember { mutableStateOf<String?>(null) }

    fun refreshFiles() {
        autoFiles = backupRepository.autoBackupFiles()
    }

    fun offerRestore(payload: BackupPayload, source: String) {
        pendingPayload = payload
        pendingPreview = BackupCodec.preview(payload)
        pendingSource = source
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = pendingExportBytes
        if (uri != null && bytes != null) {
            scope.launch {
                busy = true
                backupRepository.writeTo(uri, bytes).fold(
                    onSuccess = { count ->
                        message = "Backup cifrato esportato ($count byte). SHA-256 nel file protetto dall'involucro cifrato."
                    },
                    onFailure = { error = "Esportazione fallita: ${it.message}" },
                )
                pendingExportBytes = null
                busy = false
            }
        } else {
            pendingExportBytes = null
            message = "Esportazione annullata."
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            importError = null
            backupRepository.readUri(uri).fold(
                onSuccess = { bytes ->
                    importBytes = bytes
                    importName = uri.lastPathSegment ?: "backup Nexus"
                    importPass = ""
                    importDialog = true
                },
                onFailure = { error = "File non leggibile: ${it.message}" },
            )
            busy = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            senderCancelled = true
            runCatching { senderServer?.close() }
            senderJob?.cancel()
        }
    }

    fun startSender() {
        if (senderJob?.isActive == true) return
        val port = senderPortInput.toIntOrNull()?.takeIf { it in 1024..65535 } ?: DEFAULT_PORT
        senderCancelled = false
        senderJob = scope.launch {
            val payload = runCatching { backupRepository.payloadBytes() }
                .getOrElse { failure ->
                    sender = SenderUi("", port, "", "Preparazione fallita", failure.message)
                    return@launch
                }
            val code = TransferProtocol.generateCode()
            val addresses = localIpv4Addresses()
            sender = SenderUi(
                ip = addresses.firstOrNull() ?: "IP non rilevato",
                port = port,
                code = code,
                status = "Server in ascolto. In attesa di un dispositivo…",
            )
            withContext(Dispatchers.IO) {
                try {
                    ServerSocket().use { server ->
                        server.reuseAddress = true
                        server.bind(InetSocketAddress(port))
                        server.soTimeout = 180_000
                        senderServer = server
                        val client = server.accept()
                        sender = sender?.copy(status = "Dispositivo connesso: invio cifrato in corso…")
                        client.use { socket ->
                            TransferProtocol.send(
                                outputStream = socket.getOutputStream(),
                                payload = payload,
                                code = code,
                                appVersion = buildInfo.versionName,
                                schemaVersion = NEXUS_SCHEMA_VERSION,
                            )
                        }
                        sender = sender?.copy(status = "Inviato. Integrità verificata dal ricevente.")
                    }
                } catch (failure: Exception) {
                    if (!senderCancelled) {
                        sender = sender?.copy(
                            status = "Trasferimento interrotto",
                            error = failure.message ?: "Errore di rete locale",
                        )
                    }
                } finally {
                    senderServer = null
                }
            }
        }
    }

    fun receiveTransfer() {
        val code = receiverCode.trim()
        val host = receiverHost.trim()
        val port = receiverPort.toIntOrNull() ?: DEFAULT_PORT
        when {
            host.isBlank() -> {
                receiveError = "Inserisci l'indirizzo IP mostrato dal dispositivo che invia."
                return
            }
            !code.matches(Regex("\\d{6}")) -> {
                receiveError = "Il codice deve essere di 6 cifre."
                return
            }
            port !in 1024..65535 -> {
                receiveError = "Porta non valida."
                return
            }
        }
        receiving = true
        receiveError = null
        message = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 10_000)
                        socket.soTimeout = 30_000
                        TransferProtocol.receive(socket.getInputStream(), code)
                    }
                }
            }
            outcome.fold(
                onSuccess = { packageResult ->
                    val payload = BackupCodec.parsePayload(
                        packageResult.payload.toString(Charsets.UTF_8),
                    )
                    payload.fold(
                        onSuccess = { parsed ->
                            offerRestore(
                                parsed,
                                "Trasferimento locale da Nexus ${packageResult.appVersion}",
                            )
                            message = "Pacchetto ricevuto, decifrato e verificato (SHA-256). Controlla l'anteprima."
                        },
                        onFailure = { receiveError = "Payload ricevuto non valido: ${it.message}" },
                    )
                },
                onFailure = { failure ->
                    receiveError = "Connessione non riuscita: ${failure.message ?: "codice errato, rete o firewall"}"
                },
            )
            receiving = false
        }
    }

    NexusDetailScreen(
        title = "Backup e trasferimento",
        subtitle = "Solo locale",
        onBack = onBack,
    ) {
        NexusNotice(
            "I backup automatici sono cifrati con una chiave Android Keystore legata a questo dispositivo. Le esportazioni usano una passphrase scelta da te. Nessun dato viene caricato su cloud.",
            tone = NexusNoticeTone.INFO,
        )
        message?.let { NexusNotice(it, tone = NexusNoticeTone.SUCCESS) }
        error?.let { NexusNotice(it, tone = NexusNoticeTone.ERROR) }
        if (busy) {
            com.nexus.core.designsystem.NexusLoading("Operazione locale in corso…")
        }

        SettingsBlock("Backup automatico", "Sul dispositivo") {
            NexusToggleRow(
                title = "Backup automatico",
                subtitle = "Una copia al giorno nella memoria privata dell'app, massimo 5 copie",
                checked = settings.autoBackupEnabled,
                onCheckedChange = { enabled ->
                    settingsRepository.updateAsync { it.copy(autoBackupEnabled = enabled) }
                    if (enabled) scheduler.scheduleDaily() else scheduler.cancel()
                },
            )
            Text(
                if (settings.lastBackupAt == 0L) {
                    "Nessun backup automatico ancora creato."
                } else {
                    "Ultimo backup: ${NexusFormatters.dateWithYear(settings.lastBackupAt)} · ${settings.lastBackupStatus}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusButton(
                    "Crea backup ora",
                    {
                        scope.launch {
                            busy = true
                            error = null
                            backupRepository.autoBackup().fold(
                                onSuccess = { file ->
                                    refreshFiles()
                                    message = "Backup creato: ${file.name}"
                                },
                                onFailure = { error = "Backup non riuscito: ${it.message}" },
                            )
                            busy = false
                        }
                    },
                    leading = com.nexus.core.designsystem.NexusGlyphId.BACKUP,
                    enabled = !busy,
                )
                NexusButton(
                    "Esporta cifrato",
                    {
                        exportPass = ""
                        exportConfirm = ""
                        exportError = null
                        exportDialog = true
                    },
                    filled = false,
                    enabled = !busy,
                )
            }
        }

        if (autoFiles.isNotEmpty()) {
            SettingsBlock("Copie locali", "${autoFiles.size} disponibili") {
                autoFiles.forEach { file ->
                    AutoBackupRow(
                        file = file,
                        onRestore = {
                            scope.launch {
                                busy = true
                                error = null
                                backupRepository.decryptAuto(file).fold(
                                    onSuccess = { payload ->
                                        offerRestore(
                                            payload,
                                            "Backup automatico ${NexusFormatters.dateWithYear(payload.createdAt)}",
                                        )
                                    },
                                    onFailure = {
                                        error = "Impossibile aprire ${file.name}: ${it.message}. I backup automatici sono legati a questo dispositivo."
                                    },
                                )
                                busy = false
                            }
                        },
                        onDelete = {
                            file.delete()
                            refreshFiles()
                            message = "Copia ${file.name} eliminata."
                        },
                    )
                    NexusDivider()
                }
            }
        }

        SettingsBlock("File cifrato", "Passphrase") {
            Text(
                "Esporta configurazione, Home, note, calendario e statistiche in un file .nxb protetto da passphrase (AES-GCM, PBKDF2 con 120.000 iterazioni).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusButton(
                    "Esporta file",
                    {
                        exportPass = ""
                        exportConfirm = ""
                        exportError = null
                        exportDialog = true
                    },
                    enabled = !busy,
                )
                NexusButton(
                    "Importa file",
                    { openDocument.launch(arrayOf("*/*")) },
                    filled = false,
                    enabled = !busy,
                )
            }
        }

        SettingsBlock("Trasferimento diretto", "Stessa rete Wi-Fi") {
            Text(
                "Due dispositivi Nexus sulla stessa rete locale. Il mittente mostra IP, porta e un codice di 6 cifre; il ricevente li inserisce. Il codice non viene trasmesso: protegge il payload AES-GCM e un codice errato fallisce l'autenticazione.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sender == null) {
                OutlinedTextField(
                    value = senderPortInput,
                    onValueChange = { senderPortInput = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Porta di ascolto (1024-65535)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusButton(
                    if (sender == null) "Invia da questo dispositivo" else "Server attivo",
                    {
                        if (sender == null) startSender()
                    },
                    enabled = sender == null && senderJob?.isActive != true,
                    leading = com.nexus.core.designsystem.NexusGlyphId.FORWARD,
                )
                if (sender != null) {
                    NexusButton("Chiudi server", {
                        senderCancelled = true
                        runCatching { senderServer?.close() }
                        senderJob?.cancel()
                        sender = null
                        message = "Server di trasferimento chiuso."
                    }, filled = false)
                }
            }
            sender?.let { ui ->
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Text("INDIRIZZO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(ui.ip, style = MaterialTheme.typography.titleMedium)
                    Text("PORTA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(ui.port.toString(), style = MaterialTheme.typography.titleMedium)
                    Text("CODICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(ui.code, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(ui.status, style = MaterialTheme.typography.bodySmall)
                    ui.error?.let { NexusNotice(it, tone = NexusNoticeTone.ERROR) }
                }
            }
            Spacer(Modifier.height(10.dp))
            NexusDivider()
            Spacer(Modifier.height(10.dp))
            Text("Ricevi su questo dispositivo", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = receiverHost,
                    onValueChange = { receiverHost = it.trim().take(46) },
                    label = { Text("IP mittente") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = receiverPort,
                    onValueChange = { receiverPort = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Porta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = receiverCode,
                onValueChange = { receiverCode = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("Codice a 6 cifre") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            NexusButton(
                if (receiving) "Connessione…" else "Connetti e ricevi",
                { receiveTransfer() },
                enabled = !receiving,
                leading = com.nexus.core.designsystem.NexusGlyphId.DOWNLOAD,
            )
            receiveError?.let { NexusNotice(it, tone = NexusNoticeTone.ERROR) }
            Text(
                "Il server mittente accetta una sola connessione e si chiude. Se il trasferimento non parte, verifica Wi-Fi, isolamento client e firewall.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "Ripristinare un backup sovrascrive impostazioni, Home, note, calendario e statistiche dopo una conferma esplicita. La versione dello schema viene verificata prima dell'applicazione.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) exportDialog = false },
            title = { Text("Passphrase del backup") },
            text = {
                Column {
                    Text(
                        "Minimo 6 caratteri. Senza questa passphrase il file non può essere ripristinato: non viene salvata da nessuna parte.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportPass,
                        onValueChange = { exportPass = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = exportConfirm,
                        onValueChange = { exportConfirm = it },
                        label = { Text("Ripeti passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    exportError?.let {
                        Spacer(Modifier.height(6.dp))
                        NexusNotice(it, tone = NexusNoticeTone.ERROR)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        when {
                            exportPass.length < 6 -> exportError = "La passphrase deve avere almeno 6 caratteri."
                            exportPass != exportConfirm -> exportError = "Le passphrase non coincidono."
                            else -> {
                                exportError = null
                                busy = true
                                scope.launch {
                                    backupRepository.createPassphraseBackup(exportPass).fold(
                                        onSuccess = { bytes ->
                                            pendingExportBytes = bytes
                                            exportDialog = false
                                            busy = false
                                            createDocument.launch(
                                                "nexus-backup-${System.currentTimeMillis()}.nxb",
                                            )
                                        },
                                        onFailure = {
                                            exportError = "Creazione non riuscita: ${it.message}"
                                            busy = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                ) { Text("Esporta") }
            },
            dismissButton = {
                TextButton(onClick = { exportDialog = false }, enabled = !busy) { Text("Annulla") }
            },
        )
    }

    if (importDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) importDialog = false },
            title = { Text("Importa backup cifrato") },
            text = {
                Column {
                    Text(importName, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Inserisci la passphrase usata per esportare il file. Verranno poi mostrati anteprima, compatibilità e conteggi prima della sovrascrittura.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPass,
                        onValueChange = { importPass = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    importError?.let {
                        Spacer(Modifier.height(6.dp))
                        NexusNotice(it, tone = NexusNoticeTone.ERROR)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val bytes = importBytes ?: return@TextButton
                        if (importPass.isBlank()) {
                            importError = "Inserisci la passphrase."
                            return@TextButton
                        }
                        busy = true
                        scope.launch {
                            backupRepository.decryptPassphrase(bytes, importPass).fold(
                                onSuccess = { payload ->
                                    offerRestore(payload, importName)
                                    importDialog = false
                                    importError = null
                                    message = "Backup decifrato e verificato. Controlla l'anteprima."
                                },
                                onFailure = {
                                    importError = "Apertura non riuscita: passphrase errata o file non valido (${it.message})."
                                },
                            )
                            busy = false
                        }
                    },
                ) { Text("Apri") }
            },
            dismissButton = {
                TextButton(onClick = { importDialog = false }, enabled = !busy) { Text("Annulla") }
            },
        )
    }

    pendingPayload?.let { payload ->
        RestoreDialog(
            payload = payload,
            preview = pendingPreview,
            source = pendingSource,
            busy = busy,
            onDismiss = {
                if (!busy) {
                    pendingPayload = null
                    pendingPreview = null
                }
            },
            onConfirm = {
                val preview = pendingPreview
                if (preview?.compatible != true) return@RestoreDialog
                busy = true
                error = null
                scope.launch {
                    backupRepository.apply(payload).fold(
                        onSuccess = { applied ->
                            message = "Ripristino completato: ${applied.noteCount} note, ${applied.eventCount} eventi, ${applied.homeItems} elementi Home."
                            pendingPayload = null
                            pendingPreview = null
                        },
                        onFailure = { error = "Ripristino fallito: ${it.message}" },
                    )
                    busy = false
                }
            },
        )
    }
}

@Composable
private fun SettingsBlock(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    NexusPanel {
        NexusSectionHeader(title, subtitle)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun AutoBackupRow(file: File, onRestore: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${NexusFormatters.dateWithYear(file.lastModified())} · ${formatBytes(file.length())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Ripristina",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRestore)
                .padding(6.dp),
        )
        Text(
            "Elimina",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDelete)
                .padding(6.dp),
        )
    }
}

@Composable
private fun RestoreDialog(
    payload: BackupPayload,
    preview: BackupPreview?,
    source: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Anteprima ripristino") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Origine: $source", style = MaterialTheme.typography.labelSmall)
                Text(
                    "Creato ${NexusFormatters.dateWithYear(payload.createdAt)} da Nexus ${payload.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NexusTag("Schema ${payload.schemaVersion}")
                    NexusTag(if (preview?.compatible == true) "Compatibile" else "Non compatibile")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${preview?.noteCount ?: payload.notes.size} note · ${preview?.eventCount ?: payload.events.size} eventi · ${preview?.homeItems ?: payload.settings.homeItems.size} elementi Home · ${preview?.pages ?: payload.settings.homePages} pagine",
                    style = MaterialTheme.typography.bodySmall,
                )
                preview?.warnings?.forEach { warning ->
                    Spacer(Modifier.height(4.dp))
                    Text("• $warning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(10.dp))
                NexusNotice(
                    "Confermando, configurazione attuale, note, calendario e statistiche d'uso verranno sovrascritti. L'operazione non è reversibile senza un altro backup.",
                    tone = NexusNoticeTone.WARNING,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy && preview?.compatible == true,
            ) { Text(if (busy) "Ripristino…" else "Ripristina") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Annulla") }
        },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ITALIAN, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.ITALIAN, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun localIpv4Addresses(): List<String> = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { network -> Collections.list(network.inetAddresses) }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .mapNotNull { it.hostAddress }
        .distinct()
}.getOrDefault(emptyList())
