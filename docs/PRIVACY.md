# Privacy e gestione dei dati

Nexus Launcher non richiede account, non usa analytics remoti, non vende dati e non
carica elenchi di app, note, eventi o statistiche su server.

## Dati trattati

| Dato | Dove | Cifratura | Controllo utente |
|---|---|---|---|
| Impostazioni, Home, dock, tema | DataStore privato | sandbox Android (non cifrato a livello applicativo) | modificabili/ripristinabili |
| Note | file privato | AES-256/GCM con chiave Android Keystore | crea/modifica/elimina, export backup |
| Eventi calendario Nexus | file privato | AES-256/GCM Keystore | crea/modifica/elimina |
| Statistiche avvii Nexus | file privato | AES-256/GCM Keystore | disattiva registrazione, cancella |
| Backup automatici | directory privata | AES-256/GCM con chiave dispositivo | crea/ripristina/elimina; max 5 |
| Backup esportati | posizione scelta con SAF | AES-GCM + PBKDF2 120k | passphrase scelta dall'utente |
| Registri diagnostici/crash | directory privata | sandbox Android | esporta/cancella |
| Badge notifiche | memoria volatile | non persistito | revoca accesso al listener |
| Elenco app installate | memoria + cache icone | non persistito su disco | gestito dal sistema; app nascoste locali |
| Città meteo/impostazioni meteo | DataStore | sandbox Android | scegli città o posizione |

I **contenuti delle notifiche non vengono letti né salvati**: il listener usa solo il
nome del pacchetto e la chiave della notifica per contare. Nexus esclude le proprie
notifiche.

## Rete

Nexus effettua richieste solo verso:

| Servizio | URL | Dati inviati | Quando |
|---|---|---|---|
| Open-Meteo forecast | `api.open-meteo.com` | coordinate della località | aggiornamento meteo |
| Open-Meteo geocoding | `geocoding-api.open-meteo.com` | testo della città cercata | ricerca città |
| GitHub Releases API | `api.github.com` | nessun dato personale; user-agent Nexus | controllo aggiornamenti |
| Motore di ricerca scelto | Google/DuckDuckGo/Bing/Wikipedia | query digitata | solo quando apri un risultato Web o premi Cerca |

Il trasferimento LAN avviene direttamente tra i due dispositivi sulla rete locale; non
passa da server Nexus.

Nessun dato viene condiviso con terze parti senza un'azione esplicita dell'utente.
Il controllo aggiornamenti e il meteo sono funzioni informative e non contengono dati
personali oltre a città/coordinate necessarie al meteo.

## Autorizzazioni

- **Internet / stato rete**: meteo, ricerca web, aggiornamenti.
- **Posizione approssimativa** (facoltativa): solo meteo; in alternativa città manuale.
- **Notifiche** (facoltativa): avvisi timer e aggiornamenti; il timer funziona comunque.
- **Accesso alle notifiche** (speciale, facoltativo): badge; solo pacchetto e conteggio.
- **Ricezione avvio completato**: ripristino allarmi dopo riavvio.
- **QUERY_ALL_PACKAGES**: necessaria a un launcher per elencare tutte le app avviabili.

Non vengono richiesti contatti, SMS, microfono, fotocamera, accessibilità, posizione
precisa o amministrazione dispositivo.

## Conservazione e cancellazione

- I dati restano sul dispositivo finché l'app è installata.
- Note, eventi, statistiche e backup possono essere cancellati dalle rispettive schermate.
- I registri diagnostici si cancellano da **Impostazioni → Diagnostica**.
- La disinstallazione rimuove tutti i dati privati dell'app; esporta prima un backup.
- I backup esportati sono sotto il controllo dell'utente e non vengono tracciati da Nexus.

## Minori

Nexus non è progettato per raccogliere dati personali e non offre account o contenuti
rivolti ai minori.

## Modifiche

Questa informativa descrive la versione 1.0.0. Eventuali modifiche future saranno
documentate nelle note di rilascio e nel repository.

## Verifica

Il codice di rete è concentrato in `core:data/NexusHttp.kt`,
`core:data/WeatherRepository.kt`, `core:data/PlatformServices.kt` (update checker) e
`feature:backup` (trasferimento locale). Non esistono SDK di analytics o pubblicità.
