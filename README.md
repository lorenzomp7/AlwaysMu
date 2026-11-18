# AlwaysMu

App Android che mantiene **sempre la musica accesa**.

Ogni **30 secondi** controlla se c'è un'app di musica in riproduzione.
Se non c'è, apre l'app scelta (**Spotify, YouTube Music, Apple Music o Deezer**), preme **play**
e imposta il **volume** scelto.

## Funzioni

- Scelta dell'app musicale (le app non installate compaiono disattivate).
- Volume musica in percentuale (0–100%):
  - viene impostato ogni volta che AlwaysMu fa partire la musica;
  - con l'opzione *"Mantieni questo volume ad ogni controllo"* viene reimpostato ogni 30 s.
- Controllo ogni 30 s con un servizio in primo piano (notifica fissa con stato e pulsante *Ferma*).
- Non avvia la musica durante chiamate o con il telefono che squilla.
- Riparte da solo dopo il riavvio del telefono, se era attivo.
- Pulsante *Controlla ora* per un controllo immediato.

## Come fa partire la musica

1. Manda **play** alla sessione multimediale dell'app scelta (serve l'*Accesso alle notifiche*),
   oppure manda il tasto PLAY direttamente all'app.
2. Se dopo 3 s non suona, **apre l'app** (serve *Mostra sopra altre app*, perché Android
   blocca l'apertura di app dal background).
3. Dopo 6 s manda di nuovo play e verifica.

## Permessi da concedere (dalla schermata principale)

| Permesso | A cosa serve |
|---|---|
| Notifiche | Mostrare la notifica del servizio (Android 13+) |
| Accesso alle notifiche | Vedere quali app stanno suonando e premere play. Le notifiche non vengono lette |
| Mostra sopra altre app | Aprire l'app musicale mentre AlwaysMu è in background |
| Ignora ottimizzazione batteria | Evitare che Android fermi il servizio |

L'opzione *"Controlla anche a schermo spento"* (attiva di default) tiene sveglia la CPU,
così il controllo resta ogni 30 s anche a schermo spento. Consuma più batteria.

> Nota: l'app musicale riprende l'ultima coda o playlist. Se non c'è niente da riprendere
> (es. app appena installata o senza login), apre l'app ma potrebbe non suonare.

## Compilazione

Requisiti: JDK 17+ e Android SDK (platform 35).

```bash
./gradlew assembleDebug
```

L'APK si trova in `app/build/outputs/apk/debug/app-debug.apk`.

Struttura del codice (`app/src/main/java/com/alwaysmu/app/`):

- `MainActivity.kt`: interfaccia (scelta app, volume, permessi, avvio/stop)
- `MusicWatchdogService.kt`: servizio in primo piano con il controllo ogni 30 s
- `MusicController.kt`: rilevamento musica, volume, comandi play, apertura app
- `MusicApp.kt`: elenco app supportate con i nomi dei pacchetti
- `MediaListenerService.kt`: serve solo per accedere alle sessioni multimediali
- `BootReceiver.kt`: riavvio automatico dopo il boot

## Download dell'APK

Ogni push su `main_claude` (o `main`) avvia GitHub Actions (`.github/workflows/build-apk.yml`):
l'APK viene compilato e pubblicato nella pagina **Releases** della repo
(`AlwaysMu-1.0.<numero>.apk`). Scarica l'ultima release e installala sul telefono
(serve consentire l'installazione da origini sconosciute).

Gli APK sono firmati sempre con la stessa chiave (`app/debug.keystore`, chiave di debug
non segreta), quindi una nuova versione si installa sopra la precedente.

## "App was denied access" / impostazioni con limitazioni

Android 13+ blocca *Accesso alle notifiche* e *Mostra sopra altre app* per le app
installate da APK. Per sbloccarle: **Impostazioni → App → AlwaysMu → ⋮ (in alto a destra)
→ "Consenti impostazioni con limitazioni"**, poi attiva i due permessi.
