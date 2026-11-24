# Compatibilità delle app musicali

Che cosa offre ognuna delle 13 app di `MusicApp.KNOWN` e quale metodo di avvio
funziona davvero. Serve ai punti 8 (ripresa con `EXTRA_RECENT`), 9 (`MethodRanker`),
13 (riproduci qualcosa con ricerca vuota) e 21 (pulsante «Prova questa app») del Piano.

Stato: **da compilare**. La parte «dichiarato» si riempie con REA in Claude Desktop;
la parte «provato» va fatta sul telefono, app per app.

## REA in Claude Desktop (Windows)

Una volta sola, da PowerShell nella cartella della repo (gratis, niente amministratore):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\installa-rea-claude-desktop.ps1
```

Lo script installa ciò che manca (Node.js, adb, JDK, il JAR di JADX con controllo SHA-256,
`rea-agents@6.10.1`) e registra REA in Claude Desktop con gli strumenti Android; prima di
toccare la configurazione di Claude Desktop chiede conferma. Poi riapri Claude Desktop,
collega il telefono con il debug USB attivo e accetta l'autorizzazione sul telefono.

In una chat nuova di Claude Desktop chiedi, per esempio:

> Con REA: elenca i dispositivi adb, scarica con pull_adb_package gli APK di queste app
> (pacchetti in MusicApp.kt di AlwaysMu: com.spotify.music, com.amazon.mp3,
> deezer.android.app, com.aspiro.tidal, com.apple.android.music,
> com.google.android.apps.youtube.music, com.soundcloud.android, com.qobuz.music,
> com.idagio.app, com.mixcloud.player, com.audiomack, com.bandcamp.android,
> com.brave.browser), analizzale con inspect_android_package e riempi la tabella
> «Dichiarato nel manifest» qui sotto. Poi, mentre faccio partire la musica in ogni app,
> leggi `dumpsys media_session` e aggiungi stato e azioni della sessione.

In alternativa, senza Claude: `python scripts/audit_app_musicali.py --sessioni` fa le
stesse cose dal terminale e scrive `audit-app-musicali/<data>/riepilogo.md`.

Si leggono solo le dichiarazioni dei manifest e lo stato delle sessioni, per
interoperabilità: nessun codice delle app viene copiato.

## Dichiarato nel manifest

Che cosa significa ogni colonna:

- **MediaBrowserService**: servizio con l'azione `android.media.browse.MediaBrowserService`.
  Se è esportato, AlwaysMu può provare a riprendere la musica senza aprire l'app (punto 8).
  Molte app accettano solo client conosciuti: lo dice solo la prova.
- **Media3 session**: servizio `androidx.media3.session.MediaSessionService`
  (app costruite con Media3).
- **MEDIA_BUTTON**: ricevitore di `android.intent.action.MEDIA_BUTTON`, cioè il tasto PLAY
  inviato direttamente all'app.
- **PLAY_FROM_SEARCH**: attività con `android.media.action.MEDIA_PLAY_FROM_SEARCH`,
  da usare quando non c'è niente da riprendere (punto 13).
- **Sessione (azioni)**: `dumpsys media_session` durante la riproduzione: stato e azioni
  che la sessione dichiara (`PLAY`, `PLAY_FROM_SEARCH`, …).

| App | Pacchetto | MediaBrowserService | Media3 session | MEDIA_BUTTON | PLAY_FROM_SEARCH | Sessione (azioni) |
|---|---|---|---|---|---|---|
| Spotify | com.spotify.music | | | | | |
| Amazon Music | com.amazon.mp3 | | | | | |
| Deezer | deezer.android.app | | | | | |
| Tidal | com.aspiro.tidal | | | | | |
| Apple Music | com.apple.android.music | | | | | |
| YouTube Music | com.google.android.apps.youtube.music | | | | | |
| SoundCloud | com.soundcloud.android | | | | | |
| Qobuz | com.qobuz.music | | | | | |
| Idagio | com.idagio.app | | | | | |
| Mixcloud | com.mixcloud.player | | | | | |
| Audiomack | com.audiomack | | | | | |
| Bandcamp | com.bandcamp.android | | | | | |
| Brave Browser | com.brave.browser | | | | | |

## Provato sul telefono

Per ogni metodo: ✓ parte, ✗ non parte, – non provato. Prova con l'app chiusa
(rimossa dalle recenti) e schermo bloccato, poi con l'app in background.

| App | Sessione | Browser `EXTRA_RECENT` | Tasto all'app | Tasto di sistema | Apertura + play | Ricerca vuota | Telefono / versione app | Note |
|---|---|---|---|---|---|---|---|---|
| Spotify | – | – | – | – | – | – | | |
| Amazon Music | – | – | – | – | – | – | | |
| Deezer | – | – | – | – | – | – | | |
| Tidal | – | – | – | – | – | – | | |
| Apple Music | – | – | – | – | – | – | | |
| YouTube Music | – | – | – | – | – | – | | In background di norma suona solo con Premium (Piano, punto 21) |
| SoundCloud | – | – | – | – | – | – | | |
| Qobuz | – | – | – | – | – | – | | |
| Idagio | – | – | – | – | – | – | | |
| Mixcloud | – | – | – | – | – | – | | |
| Audiomack | – | – | – | – | – | – | | |
| Bandcamp | – | – | – | – | – | – | | |
| Brave Browser | – | – | – | – | – | – | | Dipende dalla scheda aperta (Piano, punto 33) |

Le differenze tra «dichiarato» e «provato» vanno spiegate nelle note (per esempio un
MediaBrowserService esportato che rifiuta i client sconosciuti).

## Ordine iniziale dei metodi (`MethodRanker`, punto 9)

Da decidere con i dati delle due tabelle: per ogni app il primo metodo è quello che è
partito più spesso nelle prove; senza dati resta l'ordine attuale (sessione → tasto
all'app → tasto di sistema → apertura).
