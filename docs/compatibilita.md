# Compatibilità delle app musicali

Che cosa offre ognuna delle 13 app di `MusicApp.KNOWN` e quale metodo di avvio
funziona davvero. Serve ai punti 8 (ripresa con `EXTRA_RECENT`), 9 (`MethodRanker`),
13 (riproduci qualcosa con ricerca vuota) e 21 (pulsante «Prova questa app») del Piano.

Stato (2026-11-24): **indizi da fonti pubbliche compilati** (sezione qui sotto) per tutte
le 13 app. La tabella «dichiarato nel manifest» si riempie dagli APK con lo script; la parte
«provato» va fatta sul telefono, app per app.

## Come si aggiorna, tutto dalla chat

Manda gli APK (`.apk`, `.apks`, `.xapk` o `.apkm`) in una chat di Claude Code o Claude,
oppure, in Claude Code sul PC, indica la cartella dove li hai scaricati, e scrivi:

> Fai l'audit delle app musicali con questi APK.

Claude segue la skill `audit-app-musicali`: analizza i manifest
(`scripts/audit_app_musicali.py --apk`), riempie la tabella qui sotto e fa il commit.
Il telefono non serve. REA è già collegato come strumento in ogni sessione Claude Code su
questa repo (`.mcp.json`); per leggere i manifest con REA serve JADX, che sul PC installa
`.claude\rea\installa-windows.ps1` (0 €); nel cloud e su Linux lo installa `.claude/rea/installa.sh`. Senza JADX si usa il lettore interno `scripts/axml.py`,
che legge il manifest binario senza programmi esterni.

Si leggono solo le dichiarazioni dei manifest e lo stato delle sessioni, per
interoperabilità: nessun codice delle app viene copiato.


## Indizi da fonti pubbliche (prima degli APK)

Un'app che funziona in **Android Auto** deve avere un media browser service esportato
(`MediaBrowserService` o `MediaLibraryService` di Media3): Android Auto lo usa per trovarla
e mostrarne i contenuti ([Android Developers](https://developer.android.com/training/cars/media),
[Media3 per auto](https://developer.android.com/media/implement/surfaces/cars?hl=en)). Le app che
si avviano con «Ehi Google, metti X su <app>» ricevono `onPlayFromSearch()` nella sessione, e
con query vuota devono suonare il contenuto «corrente»
([Google Assistant e app media](https://developer.android.com/media/implement/assistant)): è il
caso del punto 13. Questi sono **indizi**, non dati del manifest: vanno confermati con lo
script sugli APK e con la prova sul telefono (molte app accettano solo client noti).

| App | Android Auto | Fonte | Indizio per AlwaysMu |
|---|---|---|---|
| Spotify | sì | [Digital Trends](https://www.digitaltrends.com/phones/best-android-auto-apps/), [Google](https://support.google.com/androidauto/answer/6348327?hl=en) | MediaBrowserService probabile; comando vocale documentato da Google; utenti segnalano avvio a voce che apre l'app senza suonare ([Spotify Community](https://community.spotify.com/t5/Android/Google-Assistant-not-able-to-control-Spotify/m-p/5794597/highlight/true)) |
| Amazon Music | sì | [Android Central](https://androidcentral.com/these-apps-are-compatible-android-auto), [Google](https://support.google.com/androidauto/answer/6348327?hl=en) | MediaBrowserService probabile; comando vocale documentato |
| Deezer | sì | [SlashGear](https://www.slashgear.com/1276055/best-music-apps-android-auto/) | MediaBrowserService probabile |
| Tidal | sì | [supporto Tidal](https://support.tidal.com/hc/en-us/articles/18907040043921) | MediaBrowserService probabile; controllo anche a telefono bloccato |
| Apple Music | sì | [BGR](https://bgr.com/2114973/apps-you-might-not-realize-are-compatible-with-android-auto) | MediaBrowserService probabile |
| YouTube Music | sì | [Google](https://support.google.com/androidauto/answer/6348327?hl=en) | MediaBrowserService probabile; comando vocale documentato; in background di norma solo con Premium |
| SoundCloud | sì | [SoundCloud Help](https://help.soundcloud.com/hc/en-us/articles/1260804622950-Apple-CarPlay-Android-Auto) | MediaBrowserService probabile |
| Qobuz | sì (dalla 5.2.0, 2019), anche comandi vocali | [Qobuz](https://community.qobuz.com/news-en/2019/10/24/qobuz-is-now-compatible-with-android-auto), [Qobuz Help](https://help.qobuz.com/en/articles/602448-how-to-listen-to-qobuz) | MediaBrowserService probabile; voce dalla 6.2 |
| Idagio | sì | [supporto Idagio](https://support.idagio.com/en/articles/10232971-how-to-use-idagio-with-android-auto) | MediaBrowserService probabile |
| Mixcloud | sì (dalla 32.3.0, con voce) | [note di versione](https://www.apkmirror.com/apk/mixcloud/mixcloud-radio-dj-mixes/mixcloud-radio-dj-mixes-32-3-0-release/mixcloud-radio-dj-mixes-32-3-0-android-apk-download/) | MediaBrowserService probabile |
| Audiomack | non trovato nelle fonti consultate | — | da APK |
| Bandcamp | sì (solo la propria collezione) | [Bandcamp Help](https://get.bandcamp.help/en/articles/15263101-stream-your-bandcamp-collection-via-android-auto-apple-carplay) | MediaBrowserService probabile |
| Brave Browser | no (è un browser, non un'app media per Android Auto) | — | niente MediaBrowserService atteso: solo sessione della scheda e tasto media |

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

Proposta provvisoria dagli indizi pubblici, da confermare con le prove: per le 11 app con
Android Auto provare il browser con `EXTRA_RECENT` subito dopo la sessione (punto 8), poi
l'ordine attuale; con nulla da riprendere, ricerca vuota (punto 13). Per Audiomack, finché
mancano dati, e per Brave resta l'ordine attuale senza il browser.
