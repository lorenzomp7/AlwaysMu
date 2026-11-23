# Compatibilità delle app musicali

Che cosa offre ognuna delle 13 app di `MusicApp.KNOWN` e quale metodo di avvio
funziona davvero. Serve ai punti 8 (ripresa con `EXTRA_RECENT`), 9 (`MethodRanker`),
13 (riproduci qualcosa con ricerca vuota) e 21 (pulsante «Prova questa app») del Piano.

Stato: **da compilare**. La parte «dichiarato» si riempie con lo script qui sotto;
la parte «provato» va fatta sul telefono, app per app.

## Come si aggiorna

1. Prerequisiti, tutti gratuiti: Node.js 22+, `npm install --global rea-agents@6.10.1`
   (REA di morluto, licenza MIT), adb (Android platform-tools), JDK 17+ e il JAR di
   JADX con `REA_JADX_MCP_JAR` impostato (link e SHA-256 in `python scripts/audit_app_musicali.py --help`).
2. Telefono collegato via USB con il debug USB attivo, app musicali installate e con
   l'accesso fatto.
3. `python scripts/audit_app_musicali.py --sessioni`: per ogni app scarica l'APK, legge il
   manifest e, quando lo chiede, fai partire la musica in quell'app e premi Invio.
4. Il risultato è in `audit-app-musicali/<data>/` (non versionato): `riepilogo.md` ha le
   righe della prima tabella, `riepilogo.json` tutti i dettagli.

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
