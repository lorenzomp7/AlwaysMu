# Compatibilità delle app musicali

Che cosa offre ognuna delle 13 app di `MusicApp.KNOWN` e quale metodo di avvio
funziona davvero. Serve ai punti 8 (ripresa con `EXTRA_RECENT`), 9 (`MethodRanker`),
13 (riproduci qualcosa con ricerca vuota) e 21 (pulsante «Prova questa app») del Piano.

Stato: **da compilare**. La parte «dichiarato» si riempie dagli APK delle app;
la parte «provato» va fatta sul telefono, app per app.

## Come si aggiorna, tutto dalla chat

Manda gli APK (`.apk`, `.apks`, `.xapk` o `.apkm`) in una chat di Claude Code o Claude,
oppure, in Claude Code sul PC, indica la cartella dove li hai scaricati, e scrivi:

> Fai l'audit delle app musicali con questi APK.

Claude segue la skill `audit-app-musicali`: analizza i manifest
(`scripts/audit_app_musicali.py --apk`), riempie la tabella qui sotto e fa il commit.
Il telefono non serve. REA è già collegato come strumento in ogni sessione Claude Code su
questa repo (`.mcp.json`); per leggere i manifest con REA serve JADX, che sul PC installa
`scripts\installa-rea.ps1` (0 €). Senza JADX si usa il lettore interno `scripts/axml.py`,
che legge il manifest binario senza programmi esterni.

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
  che la sessione dichiara (`PLAY`, `PLAY_FROM_SEARCH`, …). Solo con un telefono
  collegato (`--sessione`); dagli APK resta «non provata».

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
