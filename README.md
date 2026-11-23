# Nexus Launcher

Launcher Android originale, modulare e realmente funzionante per Android 12+.
Kotlin + Jetpack Compose, Material 3, persistenza locale, cifratura Android Keystore,
APK Release firmato.

**Versione 1.0.0 — Stable** · `minSdk 31` · `targetSdk 35` · APK 21,4 MB

[APK release](https://github.com/bertoccimatteo70-commits/nexus-launcher/releases) ·
[Funzionalità](docs/FEATURES.md) · [Architettura](docs/ARCHITECTURE.md) ·
[Test](docs/TESTING.md) · [Privacy](docs/PRIVACY.md) · [Installazione](docs/INSTALL.md)

## Download e installazione

1. Scarica `NexusLauncher-1.0.0-release.apk` e il suo `.sha256` dalla pagina Releases.
2. Verifica il checksum confrontando i due valori:
   ```bash
   sha256sum NexusLauncher-1.0.0-release.apk
   cat NexusLauncher-1.0.0-release.apk.sha256
   ```
3. Installa:
   ```bash
   adb install NexusLauncher-1.0.0-release.apk
   ```
   oppure apri il file APK e autorizza l'installazione da fonte attendibile.
4. Apri Nexus e completa la procedura guidata. Quando richiesto, conferma il ruolo
   Home di Android. Per tornare al launcher precedente: **Impostazioni Android →
   App → App predefinite → Home**, oppure dalle impostazioni di Nexus.

L'APK è firmato con APK Signature Scheme v2 (RSA 4096). Certificato:
`127302046da5179e019c2d9703bcb3fdedcc0359f2c5507dff3542d6e37be166`.
I dettagli e i limiti sono in [docs/INSTALL.md](docs/INSTALL.md).

## Funzionalità

### Home reale
- Pager Home ciclico con 1–9 pagine, griglia configurabile 3–7 colonne e righe.
- Icone delle applicazioni **installate**, lette con `LauncherApps`/`PackageManager`,
  con cache icone limitata e fallback alle icone originali.
- Dock dinamico: scelte manuali più app più usate per riempire gli slot.
- Aggiunta, rimozione, spostamento, ridimensionamento (SMALL/MEDIUM/LARGE) di icone e
  widget; gestione pagine e dock in modalità modifica.
- Widget Nexus nativi: orologio, meteo, batteria, calendario e Dashboard.
- Dashboard compatta con collegamenti rapidi, meteo, eventi, batteria, statistiche.
- Layout automatico al primo avvio e ripristino dalle impostazioni.
- Badge notifiche configurabili (pallino/contatore) tramite Notification Listener.

### App Drawer
- Apertura con swipe verso l'alto dalla Home, pulsante dedicato e pannello Nexus.
- Solo applicazioni realmente installate; nessun elenco dimostrativo.
- Ricerca integrata, ordinamento alfabetico o per uso, cartelle automatiche per categoria.
- App nascoste gestibili, informazioni di sistema, apertura tramite intent del launcher.

### Ricerca globale
- Un campo per applicazioni, web, strumenti, impostazioni e calcolatrice live.
- Ranking locale per corrispondenza, avvii e lunghezza del nome.
- Provider configurabili (App, Web, Strumenti) e motore web selezionabile:
  Google, DuckDuckGo, Bing, Wikipedia.
- Nessuna cronologia delle ricerche salvata.

### Theme Studio Basic
- Tema Sistema/Chiaro/Scuro, colori dinamici Android 12+, palette Nexus originali.
- Sfondi geometrici (Vuoto, Griglia, Orbita, Aurora) oppure wallpaper scelto con SAF.
- Blur opzionale, animazioni regolabili o ridotte, sfondo animato disattivabile.
- Dimensione caratteri e icone, contrasto elevato, target tattili grandi.
- Tutte le modifiche sono persistite e applicate immediatamente.

### Cinque strumenti operativi
1. **Calcolatrice** con parentesi, potenza, percentuale, storico locale.
2. **Note** cifrate a riposo (Android Keystore), editor con ricerca, pin e cancellazione.
3. **Timer** con `AlarmManager.setAlarmClock`, notifica locale al termine, anche a app chiusa.
4. **Calendario** locale mensile con eventi, modifica, eliminazione e cifratura.
5. **Meteo** Open-Meteo (senza API key): ricerca città, posizione approssimativa,
   cache offline, errori e retry espliciti.

### Automazioni, privacy, backup
- Tema giorno/notte per fasce orarie, modalità notte calma e contrasto notturno.
- Pianificazione con AlarmManager; ricalcolo dell'aspetto a ogni apertura (Doze può
  ritardare l'allarme, senza simulare privilegi di sistema).
- Statistiche d'uso locali disattivabili e cancellabili; registri diagnostici locali
  esportabili; crash handler con report locali e quarantena delle configurazioni corrotte.
- Backup automatico giornaliero cifrato con chiave Keystore (massimo 5 copie), export
  con passphrase (AES-GCM + PBKDF2 120.000 iterazioni), import con anteprima,
  compatibilità schema e conferma di sovrascrittura.
- Trasferimento diretto LAN: IP/porta manuali, codice 6 cifre, payload AES-GCM,
  verifica SHA-256, una connessione, senza cloud.
- Onboarding interattivo con tema, Home, ruolo launcher e spiegazione autorizzazioni.
- Accessibilità: font scalabile, contrasto, icone grandi, etichette TalkBack, target ampi.

### Diagnostica e aggiornamenti
- Registri locali, export report/log, crash recenti, ricreazione configurazione.
- Controllo aggiornamenti GitHub Stable/Beta, note di rilascio e download tramite browser;
  nessuna installazione silenziosa, come richiesto da Android.

## Architettura

```
app                     integrazione, onboarding, navigazione, receiver, tema effettivo
core:model              contratti serializzabili e costanti di progetto
core:common             logica pura JVM: calcolatrice, ranking, backup, trasferimento,
                        automazioni, meteo, formattazione, settings codec
core:data               DataStore, LauncherApps, note/calendario/usage cifrati,
                        meteo, timer, badge, backup, diagnostica, update checker
core:designsystem       tema Nexus, backdrop, glifi originali, componenti accessibili
feature:home            Home, widget, dashboard, sheet di personalizzazione
feature:drawer          App Drawer reale
feature:search          ricerca globale
feature:settings        Theme Studio, Home, drawer/ricerca, accessibilità, privacy,
                        diagnostica, aggiornamenti, informazioni
feature:tools           calcolatrice, note, timer, calendario, meteo
feature:automations     scheduler AlarmManager + UI
feature:backup          backup/ripristino, export/import, trasferimento LAN
```

Dettagli, direzione delle dipendenze, scelte di sicurezza e prestazioni:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Build locale

Requisiti: JDK 17, Android SDK 35, build-tools 35.0.0.

```bash
./gradlew :core:common:test
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
```

La release usa `signing/keystore.properties` (ignorato da Git). Senza quel file Gradle
produce un APK non firmato. Non committare mai chiavi o password.

### Firma Release

Il repository include la configurazione per leggere:

```properties
# signing/keystore.properties — NON COMMITTARE
storeFile=signing/nexus-release.jks
storePassword=...
keyAlias=nexus
keyPassword=...
```

La pipeline GitHub Actions ricostruisce il keystore dai secret:
`SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`. Il workflow verifica la firma con `apksigner`, calcola
il file `.sha256` e pubblica APK + checksum nella release del tag `v*`.

### Build riproducibile

Due rebuild puliti della stessa revisione hanno prodotto 118/118 entry ZIP identiche
(byte per byte, stessa dimensione). L'APK firmato completo non è bit-identico perché il
blocco di firma APK v2 può variare; il **contenuto dell'app** è riproducibile.
Dettagli e limiti in [docs/TESTING.md](docs/TESTING.md).

## Test

- **30 test unitari JVM**: 30 passati, 0 fallimenti (`:core:common:test`), anche in CI.
- **4 test UI Compose** compilati (`:app:assembleDebugAndroidTest`); l'esecuzione in CI
  è stata tentata due volte su API 31, ma il job è fallito con
  `Timeout waiting for emulator to boot.` (600 s e 1200 s). I test UI **non sono stati
  eseguiti** e non sono da considerare superati.
- Build Release firmata verificata con `apksigner` (schema v2).
- Nessun dispositivo fisico era disponibile: installazione, ruolo Home, permessi,
  LAN a due dispositivi, TalkBack e prestazioni non sono stati verificati su hardware.
  La pipeline include il job strumentato, ma il runner GitHub non ha avviato l'emulatore.

Comandi, risultati e limiti completi: [docs/TESTING.md](docs/TESTING.md).

## Limiti dichiarati

- Le statistiche d'uso riguardano gli avvii effettuati **tramite Nexus**, non l'uso
  system-wide (che richiederebbe Usage Access e sarebbe meno trasparente).
- I badge dipendono dall'autorizzazione Notification Listener e dalla connessione del
  servizio; nessun contenuto delle notifiche viene letto.
- Gli allarmi delle automazioni sono inexact per rispettare le policy Android; Doze può
  ritardarli. Il tema viene comunque ricalcolato all'apertura.
- Il trasferimento LAN richiede la stessa rete e l'inserimento manuale di IP/porta;
  non usa discovery automatica né cloud.
- Gli aggiornamenti sono scaricati e installati tramite browser/programma di
  installazione Android; nessuna installazione silenziosa.
- Non sono implementati, per specifica: icon pack/plugin esterni, Nexus Labs, backup
  cloud, gesti aggiuntivi, lock screen alternativa.

## Privacy

Nessun account, nessun analytics remoto, nessuna vendita di dati. Rete solo per meteo
Open-Meteo, ricerca web scelta dall'utente e controllo release GitHub. Note, calendario,
statistiche e backup automatici sono cifrati localmente; i backup esportati usano una
passphrase scelta dall'utente. Dettagli: [docs/PRIVACY.md](docs/PRIVACY.md).

## Licenze

AndroidX, Jetpack Compose e Material 3 (Apache 2.0); Kotlin, kotlinx.coroutines e
kotlinx.serialization (Apache 2.0); dati meteo Open-Meteo (CC BY 4.0). Il codice Nexus
è originale.
