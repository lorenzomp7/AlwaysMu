# AlwaysMu

App Android (Kotlin, minSdk 26, targetSdk 35) che a intervalli regolari controlla
se c'è musica e, se manca, avvia l'app scelta, preme play e imposta il volume.
Lavora su `main_claude` salvo indicazione diversa: ogni push su `main_claude` o
`main` compila gli APK e **pubblica una release GitHub**.

## Struttura (`app/src/main/java/com/alwaysmu/app/`)

- `MusicWatchdogService.kt`: servizio in primo piano, controllo periodico, tentativi di avvio.
- `MusicController.kt`: rilevamento musica, volume, comandi play, apertura app (API Android).
- `VolumeMath.kt`: conversione percentuale ↔ indice volume, pura e testata.
- `Prefs.kt`, `MusicApp.kt`: impostazioni e catalogo app; la logica nel companion è testata.
- `MainActivity.kt`: interfaccia e permessi. `BootReceiver.kt`: riavvio dopo il boot.
- Due flavor: `standard` (senza accesso alle notifiche) e `completa`
  (`HAS_MEDIA_LISTENER`, manifest in `app/src/completa/`).

## Comandi (JDK 17 + Android SDK platform 35)

| Scopo | Comando |
|---|---|
| **Verifica standard** | `./gradlew testStandardDebugUnitTest` |
| Test di entrambi i flavor | `./gradlew testStandardDebugUnitTest testCompletaDebugUnitTest` |
| APK | `./gradlew assembleStandardDebug assembleCompletaDebug` |
| Test degli script (`scripts/`) | `python3 -m unittest discover -s scripts` |
| REA in Claude Code e Claude Desktop (Windows, una volta) | `powershell -ExecutionPolicy Bypass -File .claude\rea\installa-windows.ps1` |
| REA su Linux/macOS (nel cloud lo fa l'avvio della sessione) | `bash .claude/rea/installa.sh`; controllo: `node .claude/rea/check.mjs [file.apk]` |
| Audit delle app musicali dagli APK (senza telefono) | `python3 scripts/audit_app_musicali.py --apk <file o cartelle>` → `docs/compatibilita.md` |

I test JVM stanno in `app/src/test/`. Esegui la verifica standard prima di
dichiarare finito un cambio; se l'SDK Android non è disponibile dillo invece di
dichiarare i test passati.

## Regole del progetto

- Metti la logica decisionale nuova in funzioni pure (come `VolumeMath`) con un
  test JVM; lascia in `MusicController` solo le chiamate alle API Android.
- Non avviare mai la musica durante chiamate o suoneria (`isInCall`).
- Non rompere la compatibilità delle preferenze salvate: chiavi in `Prefs`,
  conversione delle vecchie chiavi in `MusicApp.normalizeKey`.
- Il flavor `standard` non deve dichiarare l'accesso alle notifiche (Play Protect).
- `debug.keystore` è voluto (firma stabile tra build): non cambiarlo.
- Testi dell'interfaccia in italiano (`res/values/strings.xml`).

## Autore dei commit

- I commit vanno a nome di Lorenzo, così risulta tra i contributor della repo:
  `git -c user.name="Lorenzo Mainetti" -c user.email="119352170+lorenzomp7@users.noreply.github.com" commit ...`
- L'agente si indica come co-autore con la riga `Co-Authored-By:` nel messaggio.
- Per i cambi solo di documentazione aggiungi `[skip ci]` al titolo, così non
  viene pubblicata una release APK inutile.
