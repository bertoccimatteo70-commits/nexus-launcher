# Test e verifiche

Data delle verifiche: 23 novembre 2026.
Ambiente: Linux cloud, JDK Corretto 17, Android SDK 35 / build-tools 35.0.0,
Gradle 8.10.2. Nessun dispositivo fisico collegato, emulatore non installato.

## Comandi eseguiti

```bash
./gradlew :core:common:test
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt2 dump badging app/build/outputs/apk/release/app-release.apk
```

Nell'ambiente è stato usato il Gradle installato localmente (8.10.2); il repository
include il wrapper `./gradlew` per la CI.

## Risultato test unitari

```
BUILD SUCCESSFUL
SUITES=4  TESTS=30  FAILURES=0  ERRORS=0  SKIPPED=0
```

Suite:

- `CalculatorTest` — 6 test: precedenza, parentesi, decimali, divisione per zero,
  potenza, input non valido.
- `SearchAndAutomationTest` — 7 test: ranking, provider, categorie, automazioni
  giorno/notte, transizioni.
- `SettingsAndBackupTest` — 11 test: codec impostazioni, sanitizzazione schema,
  backup con passphrase, manomissione, backup device-key, preview, trasferimento
  loopback, codice errato, protocollo non supportato, versioning.
- `WeatherApiTest` — 6 test: parsing Open-Meteo, codici WMO, geocoding, dati mancanti,
  fallback.

Report generati in `core/common/build/reports/tests/test/index.html` (artefatto di build,
non committato).

## Compilazione

| Target | Esito |
|---|---|
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| `:app:assembleRelease` | BUILD SUCCESSFUL |
| Moduli `core:*` e `feature:*` | Compilati come dipendenze |

Il test APK contiene 4 test Compose strumentati:

- `primaryActionIsClickableAndVisible`
- `themeChoiceUpdatesState`
- `iconButtonExposesAccessibleLabel`
- `sliderValueLabelIsReadable`

Non sono stati **eseguiti** perché `adb devices` non riporta dispositivi e l'emulatore
non è installato. La CI include un job `reactivecircus/android-emulator-runner` con API 31
che li eseguirà su push/PR.

## Release APK

| Campo | Valore |
|---|---|
| File | `NexusLauncher-1.0.0-release.apk` |
| Byte | 21.445.955 |
| SHA-256 | `b5087d773f7f95993fa96f6b0a32c956cde51e7ae62f488f98822b4fcdda5ccf` |
| Certificato | `CN=Nexus Launcher, OU=Release, O=Nexus Project, L=Milano, ST=MI, C=IT` |
| Certificato SHA-256 | `127302046da5179e019c2d9703bcb3fdedcc0359f2c5507dff3542d6e37be166` |
| Chiave | RSA 4096 |
| Firma | APK Signature Scheme v2: verificata |
| minSdk / target | 31 / 35 |
| Package | `com.nexus.launcher` |
| Versione | 1.0.0 (versionCode 1) |

`apksigner verify --verbose` riporta `Verifies` e
`Verified using v2 scheme (APK Signature Scheme v2): true`.
La v1 non è abilitata (non necessaria per Android 12+); v3/v4 non sono presenti.
L'APK non è un debug: applicationId senza suffisso `.debug`, build type `release`.

Manifest verificato con `apkanalyzer`/`aapt2`:

- activity con `MAIN` + `HOME` + `DEFAULT` (ruolo launcher);
- anche `MAIN` + `LAUNCHER` per apertura normale;
- Notification Listener con `BIND_NOTIFICATION_LISTENER_SERVICE`;
- receiver timer, backup, automazioni e boot;
- permessi: Internet, stato rete, posizione approssimativa, notifiche, boot,
  `QUERY_ALL_PACKAGES`.

## Riproducibilità

Sono state confrontate due build Release della stessa revisione:

- stesse dimensioni: 21.445.955 byte;
- 118/118 entry ZIP identiche byte per byte (hash SHA-256 di ogni entry);
- hash dell'APK diverso esclusivamente nel blocco di firma APK v2 (4.987 byte
  differenti, tutti nel signing block).

Conclusione misurata: **il contenuto dell'app è riproducibile; il file APK firmato non è
bit-identico** perché la firma può includere materiale casuale. Non viene dichiarata una
riproducibilità bit-per-bit dell'artefatto firmato.

## Cosa non è stato verificato

Nessuna delle seguenti voci è stata eseguita in questo ambiente:

1. installazione e avvio su Android 12/13/14/15;
2. richiesta e mantenimento del ruolo Home;
3. elenco reale delle app e apertura delle stesse;
4. badge da Notification Listener;
5. permessi runtime notifiche/posizione;
6. timer con app chiusa e riavvio;
7. backup automatico pianificato e ripristino reale;
8. trasferimento LAN tra due dispositivi;
9. TalkBack/audit accessibilità;
10. prestazioni e memoria su dispositivo con 2 GB;
11. controllo aggiornamenti contro la release GitHub (la release viene creata dopo
    questa build);
12. workflow GitHub Actions (non eseguito in questo ambiente; incluso nel repository).

Queste voci sono riportate come **limiti**, non come test superati.

## CI/CD

`.github/workflows/android.yml` include:

- build e test unitari su push/PR;
- compilazione APK debug e test APK;
- job strumentato su emulatore Android API 31;
- su tag `v*`: ricostruzione keystore dai secret, build Release, verifica `apksigner`,
  SHA-256, pubblicazione GitHub Release con APK e `SHA256SUMS`.

Secret richiesti: `SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`. Nessuna chiave o password è nel repository.
