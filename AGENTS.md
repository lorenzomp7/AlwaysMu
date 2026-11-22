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
