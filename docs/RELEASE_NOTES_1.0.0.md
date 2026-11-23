# Nexus Launcher 1.0.0 — Release Notes

Data: 23 novembre 2026
Canale: Stable
Package: `com.nexus.launcher`
Versione: `1.0.0` (versionCode 1)
Android minimo: 12 (API 31)
Target: Android 15 (API 35)

## Artefatto

| Campo | Valore |
|---|---|
| File | `NexusLauncher-1.0.0-release.apk` |
| Dimensione | circa 21,4 MB |
| SHA-256 | pubblicato nell'asset `NexusLauncher-1.0.0-release.apk.sha256` |
| Firma | APK Signature Scheme v2, RSA 4096 |
| Certificato SHA-256 | `127302046da5179e019c2d9703bcb3fdedcc0359f2c5507dff3542d6e37be166` |
| Build | Release, `isMinifyEnabled=false`, firmata |

L'APK della release è ricostruito e firmato dalla pipeline GitHub Actions a partire dal
tag/commit di rilascio. L'hash del file effettivamente scaricato è nel suo asset
`.sha256`, generato dalla pipeline sul file finale. La build locale verificata in fase
di sviluppo è documentata in [TESTING.md](TESTING.md).

## In evidenza

- Home reale con pager ciclico, griglia 3–7 colonne, dock dinamico, icone delle app
  installate, modifica di item/widget/pagine/dock, layout automatico.
- Widget Nexus, Dashboard compatta, meteo con cache offline, batteria, calendario.
- App Drawer con ricerca, categorie automatiche, ordinamento e app nascoste.
- Ricerca globale con app, web, strumenti, impostazioni e calcolatrice.
- Theme Studio Basic: tema chiaro/scuro/sistema, colori dinamici, palette, sfondi,
  wallpaper SAF, blur, animazioni, font e icone.
- Calcolatrice, note cifrate, timer con AlarmManager, calendario locale e meteo.
- Automazioni giorno/notte con modalità notte calma e contrasto.
- Backup automatico cifrato Keystore, export/import con passphrase, anteprima e conferma;
  trasferimento LAN con codice 6 cifre e verifica SHA-256.
- Onboarding guidato, accessibilità, privacy, diagnostica locale, aggiornamenti in-app.

## Correzioni e qualità

- 30 test unitari JVM verdi, anche nella CI GitHub Actions.
- 4 test UI Compose compilati; l'esecuzione strumentata in CI è stata tentata due volte
  su API 31 ed è fallita per `Timeout waiting for emulator to boot.` (600 s e 1200 s).
  I test UI non sono stati eseguiti.
- Build Debug, test APK e Release completate nell'ambiente di build e nella CI.
- Firma verificata con `apksigner`.
- Contenuto APK riproducibile tra rebuild (118/118 entry ZIP identiche); la firma APK
  può differire a livello di blocco di firma.

## Limitazioni note

- Nessun test su dispositivo fisico: installazione, ruolo Home, permessi runtime,
  TalkBack e LAN a due dispositivi non sono stati eseguiti. Il job CI strumentato è
  stato eseguito due volte, ma il runner GitHub non ha completato il boot
  dell'emulatore (`Timeout waiting for emulator to boot.`), quindi i test UI non sono
  stati eseguiti.
- Statistiche d'uso limitate agli avvii tramite Nexus.
- Badge dipendenti dall'accesso Notification Listener.
- Allarmi automazioni inexact (Doze può ritardarli; il tema si ricalcola all'apertura).
- Trasferimento LAN manuale (IP/porta), stessa rete, una connessione.
- Aggiornamento installato manualmente tramite Android.
- Non inclusi per specifica: icon pack/plugin, Labs, backup cloud, gesti extra,
  lock screen alternativa.

## Installazione rapida

```bash
sha256sum NexusLauncher-1.0.0-release.apk
adb install -r NexusLauncher-1.0.0-release.apk
```

Poi completa l'onboarding e imposta Nexus come Home. Per tornare indietro:
Impostazioni Android → App → App predefinite → Home.
