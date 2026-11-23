# Report delle funzionalità

Legenda verificabilità:

- **Compilato**: il modulo e l'app Release compilano.
- **Test JVM**: coperto da test unitari sulla logica pura.
- **Non eseguito su device**: implementato e compilato, ma in questo ambiente non c'erano
  dispositivo fisico o emulatore per verificare il flusso runtime.

> Nexus Launcher 1.0.0 non è un mockup. Legge app, notifiche, meteo e dati reali del
> dispositivo. Tuttavia non è stato possibile eseguire un test end-to-end su hardware
> Android: le voci sotto indicano con precisione cosa è stato verificato e cosa no.

## Requisiti di piattaforma

| Requisito | Stato |
|---|---|
| Kotlin + Jetpack Compose | Implementato; compilato |
| Android 12+ (`minSdk 31`) | Configurato; verificato nel manifest/build |
| Target Android 15 (`targetSdk 35`) | Configurato; verificato con `aapt2` |
| Architettura modulare | 12 moduli Gradle; compilati |
| APK ≤ 200 MB | 21.445.955 byte |
| APK Release firmato | Firma v2 RSA 4096 verificata con `apksigner` |
| Build CI | Workflow GitHub Actions incluso (build, unit, UI, release) |
| Test automatici | 30 unitari verdi; 4 UI compilati, non eseguiti qui |

## Home

| Funzione | Stato | Note |
|---|---|---|
| Lettura app installate | Compilato, non eseguito su device | `LauncherApps.getActivityList` + `PackageManager`; esclusione del pacchetto Nexus |
| Icone originali | Compilato, non eseguito su device | Cache LRU limitata; fallback a icona applicazione |
| Adaptive Color | Compilato | Tinta della palette Nexus; originale ripristinabile |
| Griglia 3–7 colonne | Compilato; logica settings testata JVM | Righe 3–7 usate dal layout automatico |
| Pagine multiple e cicliche | Compilato | Pager infinito con conteggio configurabile 1–9 |
| Dock dinamico | Compilato | Scelte manuali + riempimento con app più usate |
| Etichette configurabili | Compilato | Persistite in DataStore |
| Aggiunta/rimozione/spostamento icone | Compilato | Picker app reali; posizione per pagina/ordine |
| Ridimensionamento widget | Compilato | SMALL/MEDIUM/LARGE persistiti per item |
| Modifica pagine e dock | Compilato | Sheet dedicate, conferme |
| Layout automatico | Compilato | Creato al primo avvio con app più usate e widget meteo |
| Orologio/data/batteria/calendario | Compilato | Widget Nexus nativi, aggiornati dallo stato reale |
| Sfondi animati | Compilato | Campo geometrico Canvas; disattivabile |
| Blur | Compilato | Opzionale, default off per dispositivi 2 GB |
| Wallpaper utente | Compilato | SAF `OpenDocument` + permesso persistente |
| Swipe verso l'alto per drawer | Compilato | Gesto sulla Home |
| Pulsante Nexus Panel | Compilato | Collegamenti rapidi, includes drawer/ricerca/tool |
| Badge notifiche | Compilato, non eseguito su device | Richiede Notification Listener; solo pacchetto+conteggio |
| Tablet/pieghevoli | Compilato | `resizeableActivity`, layout responsive; non testato su hardware |
| Prestazioni 2 GB | Progettato | Cache limitata, niente animazioni obbligatorie, R8 disattivato; nessun benchmark su device |

## App Drawer

| Funzione | Stato |
|---|---|
| Elenco app realmente installate | Compilato, non eseguito su device |
| Apertura app tramite intent/componente | Compilato, non eseguito su device |
| Ricerca integrata | Compilato |
| Ordinamento alfabetico | Compilato; test JVM dell'ordinamento |
| Ordinamento per uso | Compilato; usa statistiche locali Nexus |
| Cartelle automatiche per categoria | Compilato; categorizzazione JVM testata |
| App nascoste | Compilato; persistite |
| Info di sistema per app | Compilato |
| Badge | Compilato, dipende dall'autorizzazione |

## Ricerca globale

| Funzione | Stato |
|---|---|
| Ranking app | Compilato; test JVM |
| Calcolatrice live | Compilato; motore testato JVM |
| Strumenti e impostazioni | Compilato |
| Web con motore scelto | Compilato; apertura browser |
| Provider configurabili | Compilato; persistiti |
| Cronologia ricerche | Non salvata, per privacy |
| Ricerca AI/modelli esterni | Non implementata per specifica |

## Personalizzazione / Theme Studio Basic

Tema sistema/chiaro/scuro, colori dinamici, palette originali, sfondi geometrici,
wallpaper, griglia, dock, dimensione icone e caratteri, personalizzazione widget,
velocità animazioni, riduzione movimento, blur, contrasto, target tattili: **compilati**
e persistenti. L'anteprima e le impostazioni sono collegate a `NexusSettings`; i test JVM
coprono sanitizzazione e roundtrip delle impostazioni.

## Widget e Dashboard

Widget Nexus nativi integrati nella Home per orologio, meteo, batteria, calendario e
dashboard; ridimensionabili e riordinabili. La Dashboard compatta mostra ora, meteo,
eventi, batteria, notifiche e collegamenti rapidi. Il meteo gestisce cache, offline,
errore, retry e ricerca città. **Compilato, non eseguito su device.**

Non viene ospitato l'AppWidgetHost di widget di terze parti: non richiesto dalla
specifica Nexus.

## Automazioni

| Regola | Stato |
|---|---|
| Tema automatico giorno/notte | Compilato; regole pure testate JVM |
| Orari inizio giorno/notte | Compilato; persistere + ripianificazione |
| Modalità notte calma | Compilato |
| Contrasto notturno | Compilato |
| Attivazione/disattivazione | Compilato |
| AlarmManager | Compilato; receiver registrato; `setAndAllowWhileIdle` |
| Riavvio dispositivo | Compilato; `BootReceiver` |
| Garanzia oraria esatta | Limitata da Doze: nessun privilegio exact-alarm richiesto; UI ricalcola a ogni apertura |

## Strumenti

| Strumento | Stato |
|---|---|
| Calcolatrice | Compilato; motore ricorsivo testato (6 test) |
| Note cifrate | Compilato; archivio AES-GCM Keystore, CRUD, ricerca, pin |
| Timer | Compilato; `setAlarmClock`, receiver, notifica, persistenza |
| Calendario | Compilato; archivio cifrato, vista mensile, CRUD |
| Meteo | Compilato; Open-Meteo, ricerca città, posizione, cache, errori |

## Navigazione e integrazione Android

- Ruolo Home richiesto tramite `RoleManager`, con fallback alle impostazioni Android.
- Pulsanti di navigazione Android gestiti con `BackHandler` e schermate dettaglio.
- Ritorno al launcher precedente tramite impostazioni Android.
- Nexus Panel per collegamenti rapidi.
- Lock screen originale non modificata.
- Nessuna funzione di sistema privilegiata simulata.

## Accessibilità

Font scalabile (85–160%), contrasto elevato, icone 40–80 dp, target tattili fino a 56 dp,
etichette `contentDescription`, descrizioni di stato per badge e selezioni, riduzione
movimento, anteprima testuale. **Compilato; TalkBack non testato su device.**

## Privacy, sicurezza, backup

| Funzione | Stato |
|---|---|
| Statistiche locali e disattivabili | Compilato; cifrate |
| Cancellazione statistiche | Compilato |
| Configurazione cifrata (note/calendario/usage) | Compilato; Keystore AES-GCM |
| Registri locali + crash handler | Compilato |
| Export diagnostica | Compilato; SAF |
| Quarantena configurazione corrotta | Implementato in `SettingsRepository` |
| Backup automatico giornaliero | Compilato; cifrato con chiave dispositivo, max 5 |
| Export con passphrase | Compilato; AES-GCM/PBKDF2 testato JVM |
| Import con anteprima | Compilato; verifica schema/integrità |
| Conferma sovrascrittura | Compilato |
| Trasferimento LAN | Compilato; protocollo testato JVM su stream, non su due dispositivi |
| Backup cloud | Non implementato per specifica |
| Icon pack/plugin/Labs/gesti extra | Non implementati per specifica |

## Onboarding

Quattro passi: benvenuto, tema/sfondo, Home/griglia, launcher/permessi/privacy/backup.
Azioni reali, persistenza immediata, possibilità di salvare e riprendere? No: la
procedura si completa o si salta; i dati già scelti restano. **Compilato, non eseguito su
device.**

## Aggiornamenti

Controllo GitHub Stable/Beta, note release, versione, link download; nessuna installazione
silenziosa. Controllo automatico al massimo una volta ogni 24 ore, più controllo manuale.
**Compilato, non eseguito contro una release reale in questo ambiente.** Il repository
deve ancora pubblicare la release 1.0.0 perché il controllo la rilevi.

## Diagnostica e stabilità

Crash handler locale, log ruotati (512 KB), report crash, export e cancellazione,
ricreazione configurazione, info dispositivo. **Compilato; non è stato possibile
verificare un crash reale su device.**

## Non completato / non verificato

1. **Verifica runtime su Android 12+**: nessun dispositivo/emulatore disponibile qui.
2. **Test LAN reale tra due dispositivi**: solo protocollo su stream JVM.
3. **TalkBack e accessibilità sul campo**: etichette presenti, nessun audit su device.
4. **Benchmark su 2 GB**: nessuna misura di FPS/memoria su hardware reale.
5. **Widget di terze parti**: esclusi per specifica (solo widget Nexus).
6. **Statistiche system-wide**: solo avvii tramite Nexus, scelta privacy.
7. **Notifica di sistema per aggiornamenti**: l'avviso è in-app (Dashboard/Home/Impostazioni),
   non una notifica di sistema separata.
