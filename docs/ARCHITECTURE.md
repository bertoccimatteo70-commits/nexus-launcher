# Architettura Nexus

## Principi

1. **Moduli con dipendenze in una sola direzione**: `app → feature:* → core:*`.
2. **Logica pura nei moduli JVM** (`core:model`, `core:common`) per test rapidi e
   riproducibili senza emulatore.
3. **Una sola fonte di verità** per le impostazioni: DataStore transazionale.
4. **Nessuna funzione simulata**: app, notifiche, meteo, timer e backup usano API Android
   reali; dove il sistema impone limiti, l'interfaccia li dichiara.
5. **Niente dipendenze inutili**: Compose, DataStore, kotlinx.serialization; DI manuale,
   nessun framework di navigazione o database esterno.

## Moduli

| Modulo | Tipo | Responsabilità |
|---|---|---|
| `core:model` | JVM | modelli serializzabili, `NexusSettings`, navigator, costanti progetto |
| `core:common` | JVM | calcolatrice, ranking, ordinamento/categorie app, meteo parsing, automazioni, codec impostazioni, backup, protocollo trasferimento, versioning |
| `core:data` | Android library | DataStore, LauncherApps, icone con cache LRU, vault cifrati, meteo HTTP/cache, timer, badge, backup, diagnostica, update checker, scheduler |
| `core:designsystem` | Android library | tema/typography/metriche, backdrop Canvas, glifi originali, componenti e icone app |
| `feature:home` | Android library | Home, pager, widget, dashboard, sheet, picker |
| `feature:drawer` | Android library | app drawer |
| `feature:search` | Android library | ricerca globale |
| `feature:settings` | Android library | impostazioni, Theme Studio, privacy, diagnostica, update, about |
| `feature:tools` | Android library | 5 strumenti |
| `feature:automations` | Android library | scheduler e schermata automazioni |
| `feature:backup` | Android library | backup, export/import, trasferimento LAN |
| `app` | Android application | container, MainActivity, onboarding, shell, receiver, manifest |

Le feature non conoscono altre feature: la navigazione passa da `NexusNavigator`,
costruito dall'app. Il `NexusContainer` crea un'unica istanza dei repository.

## Stato e persistenza

- `SettingsRepository`: DataStore Preferences con blob JSON versionato. Ogni modifica
  passa da `edit`, viene sanitizzata e salvata. Un blob illeggibile viene registrato,
  sostituito con default sicuri e riparato; il file originale può essere quarantenato.
- Impostazioni: `NexusSettings` con `schemaVersion`, range validati in `sanitized()`.
- Note, calendario, statistiche: `SecureJsonStore` su file cifrati AES-256/GCM con chiavi
  non esportabili generate in Android Keystore.
- Backup automatici: involucro cifrato con chiave Keystore dedicata, conservati nella
  memoria privata, massimo 5 copie; quello più vecchio viene potato.
- Backup esportati: involucro autoconsistente con schema, versione, modalità, salt, IV,
  ciphertext e SHA-256 del plaintext; passphrase PBKDF2-HMAC-SHA256 120.000 iterazioni.
- Timer: snapshot JSON in memoria privata; allarme `setAlarmClock`; receiver di sistema.
- Diagnostica: log ruotato da 512 KB e crash report nella directory privata; nessun upload.

## Tema effettivo

`AutomationRules.effectiveAppearance(settings, now)` è una funzione pura che combina:

- tema manuale / orario giorno-notte;
- modalità notte e notte calma;
- contrasto elevato;
- riduzione movimento.

L'app ricalcola l'aspetto ogni 60 secondi e a ogni modifica delle impostazioni, quindi
l'interfaccia è corretta anche se AlarmManager viene ritardato. `AutomationScheduler`
pianifica la prossima transizione con `setAndAllowWhileIdle`; il receiver la ripianifica.
Il boot receiver ripristina automazioni, backup e timer dopo riavvio/aggiornamento.

## Dati app e icone

`AppCatalogRepository` usa `LauncherApps.getActivityList` per l'utente corrente, esclude
il pacchetto Nexus, categorizza con euristiche testabili e aggiorna su package change.
Le icone sono caricate su IO con semaforo (3 richieste concorrenti) e cache LRU calcolata
in base alla memoria massima della VM (3–12 MB). Le dimensioni richieste sono limitate a
24–256 px. Le icone adattive sono una tinta opzionale della palette; l'originale resta
disponibile.

## Ricerca

`SearchRanking` combina:

- corrispondenza esatta/prefix/substring sul nome app;
- popolarità dagli avvii Nexus;
- provider abilitati (App/Web/Strumenti);
- calcolatrice live;
- strumenti e destinazioni impostazioni.

Il web non viene interrogato finché l'utente non sceglie un risultato o preme Cerca.
Nessuna cronologia viene scritta.

## Automazioni

Solo regole consentite a un'app normale: tema e proprietà visive. Non viene modificato
il sistema, la lock screen o impostazioni che richiedono privilegi. L'allarme è inexact
per non richiedere `SCHEDULE_EXACT_ALARM`; la UI è la fonte di verità visiva e si
aggiorna da sola.

## Backup e trasferimento

`BackupPayload` contiene impostazioni, note, eventi e statistiche in forma strutturata.
`BackupCodec` verifica formato, schema e hash prima di applicare. L'applicazione avviene
solo dopo anteprima e conferma.

Trasferimento LAN:

1. il mittente crea un payload JSON, genera un codice a 6 cifre e apre un
   `ServerSocket` sulla porta scelta;
2. cifra il payload con AES-GCM e una chiave PBKDF2 derivata dal codice;
3. invia header JSON con versione protocollo/schema, salt, IV, lunghezza, SHA-256;
4. il ricevente inserisce IP/porta/codice, decifra, verifica il digest e mostra
   l'anteprima;
5. l'applicazione richiede conferma di sovrascrittura.

Il codice non viaggia in chiaro. Il server accetta una connessione e si chiude. Non viene
usato alcun server cloud o account.

## Prestazioni

- Cache icone limitata e caricamento concorrente controllato.
- Widget aggiornati da Flows condivisi; niente polling di rete continuo.
- Meteo con cache configurabile (default 20 minuti) e fallback offline.
- Animazioni disattivabili o ridotte; sfondo Canvas a basso costo; blur opt-in.
- R8 disabilitato nella 1.0.0 per ridurre il rischio di comportamenti non verificabili
  senza test su device; il APK resta ampiamente sotto 200 MB.
- Nessuna dipendenza pesante (no Hilt, Room, OkHttp, navigation).

## Sicurezza

- Chiavi AES nel Keystore, non esportabili, senza password salvate.
- Backup esportati con passphrase e KDF iterato.
- `allowBackup=false` per evitare backup cloud automatici di Android.
- Manifest minimale: niente permessi contatti, SMS, microfono, accessibilità o
  amministrazione dispositivo.
- Nessuna installazione automatica di APK.
- Log e crash report locali, esportabili solo su azione dell'utente.

## Test

La logica critica è in `core:common` e viene testata su JVM: calcolatrice, ricerca,
categorie, automazioni, meteo, codec impostazioni, backup, trasferimento, versioning.
I test UI Compose verificano azione primaria, selezione tema, etichetta accessibile e
slider; sono compilati e pronti per emulatore/CI.
