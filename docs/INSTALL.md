# Installazione e configurazione

## Requisiti

- Android 12 o superiore (`minSdk 31`).
- Smartphone, tablet o pieghevole; RAM consigliata 2 GB o più.
- APK Release `NexusLauncher-1.0.0-release.apk` (21,4 MB, sotto il limite di 200 MB).
- Nessun account, nessun servizio cloud.

## Verifica del file

SHA-256:

```
cd1eed822077f8062a87a59deaa1db94f40b903112531fc8eb7e5e227e0f5eac  NexusLauncher-1.0.0-release.apk
```

```bash
sha256sum NexusLauncher-1.0.0-release.apk
```

Firma: APK Signature Scheme v2, RSA 4096.
Certificato (SHA-256): `127302046da5179e019c2d9703bcb3fdedcc0359f2c5507dff3542d6e37be166`.

Verifica con Android SDK build-tools:

```bash
apksigner verify --verbose --print-certs NexusLauncher-1.0.0-release.apk
```

L'APK **non** è un APK debug: la release è stata ricostruita e firmata dalla pipeline
GitHub Actions con `assembleRelease` e verificata con `apksigner`.

## Installazione

### Con ADB

```bash
adb install -r NexusLauncher-1.0.0-release.apk
```

Se sul dispositivo esiste già una versione con firma diversa, Android rifiuta
l'installazione. Disinstalla la vecchia copia solo dopo aver esportato un backup: note,
calendario e backup automatici risiedono nella memoria privata dell'app e vengono rimossi
con la disinstallazione.

### Manuale

1. Copia l'APK sul dispositivo.
2. Apri il file con il gestore file.
3. Autorizza l'installazione da questa fonte se richiesto.
4. Apri **Nexus Launcher**.

## Primo avvio

La procedura guidata permette di:

- scegliere tema, palette, sfondo e animazioni;
- configurare griglia, dock e dimensione icone;
- impostare Nexus come Home predefinita;
- leggere la spiegazione delle autorizzazioni facoltative;
- attivare backup automatico e statistiche locali.

Le autorizzazioni sono tutte facoltative:

| Autorizzazione | Uso | Se negata |
|---|---|---|
| Notifiche | avvisi timer, aggiornamenti in-app | il timer scatta comunque, senza notifica |
| Posizione approssimativa | meteo locale | usa la città scelta manualmente |
| Accesso notifiche | pallini/conteggi sulle icone | nessun badge; il resto funziona |
| Internet | meteo, ricerca web, aggiornamenti | funzioni offline disponibili |
| QUERY_ALL_PACKAGES | elenco app per un launcher | necessario per mostrare tutte le app installate |

## Impostare Nexus come launcher predefinito

- Dal primo avvio: pulsante **Imposta Nexus**.
- In seguito: **Impostazioni → Home e grid** oppure **Impostazioni → Informazioni →
  Imposta Nexus come launcher predefinito**.
- Android mostra la schermata di conferma del ruolo Home.

## Tornare al launcher precedente

1. Apri **Impostazioni Android → App → App predefinite → Home**.
2. Scegli il launcher precedente.
3. In alternativa: **Impostazioni → App → Nexus Launcher → Apri per impostazione
   predefinita → Disattiva** (i nomi variano tra produttori).

Nexus non tenta di bloccare il cambio di launcher né di sostituire la lock screen.

## Aggiornamenti

Nexus controlla le release GitHub del canale scelto (Stable/Beta) e mostra versione,
note e link. Il download avviene tramite browser; l'installazione passa dal programma
di installazione Android. Non esiste installazione automatica o silenziosa.

## Disinstallazione e dati

La disinstallazione rimuove:

- impostazioni, Home, note cifrate, calendario, statistiche;
- backup automatici interni;
- registri diagnostici.

Prima di disinstallare esporta un backup `.nxb` con passphrase da **Impostazioni →
Backup e trasferimento**. Il file esportato è tuo e resta dove lo salvi.
