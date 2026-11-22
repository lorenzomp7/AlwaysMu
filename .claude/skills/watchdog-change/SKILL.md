---
name: watchdog-change
description: Procedura per modificare come AlwaysMu rileva la musica, sceglie e avvia l'app, imposta il volume o pianifica i controlli. Usala per ogni modifica a MusicWatchdogService, MusicController, Prefs, MusicApp o ai permessi nel manifest.
---

# Modificare il servizio che tiene la musica accesa

## 1. Dove sta cosa
- `MusicWatchdogService.kt`: servizio in primo piano; `scheduleNext` → `runCheck` →
  `startPlayback` → `openAndPlay` → `verifyFinal`. Un solo tentativo alla volta
  (`attemptInProgress`), wake lock parziale solo quando serve.
- `MusicController.kt`: chiamate alle API Android (audio, sessioni media, avvio app).
- `VolumeMath.kt`, companion di `Prefs` e `MusicApp`: logica pura, testata sulla JVM.
- `BootReceiver.kt`: riavvio dopo il boot. Manifest: permessi e tipo del servizio.

## 2. Come scrivere la modifica
- Ogni decisione nuova (soglie, intervalli, priorità tra app, conversioni) va in una
  funzione pura in Kotlin senza import `android.*`, con un test in `app/src/test/`.
  `MusicController` e il servizio restano un sottile strato che chiama Android.
- Mai avviare la musica durante chiamate o suoneria: il controllo `isInCall()` viene prima di tutto.
- Nessun ciclo stretto: ogni riprova passa da `handler.postDelayed`, con intervallo
  minimo `Prefs.MIN_INTERVAL_S`.
- Il wake lock va sempre rilasciato (anche in `onDestroy` e nei rami di errore).
- Preferenze salvate: non rinominare le chiavi in `Prefs`; per cambiare formato aggiungi
  una conversione come `MusicApp.normalizeKey` e un test con il valore vecchio.

## 3. Flavor
- `standard`: senza accesso alle notifiche (`HAS_MEDIA_LISTENER = false`), si installa
  senza blocchi di Play Protect. Non aggiungere a `app/src/main/AndroidManifest.xml`
  permessi o servizi che richiedono l'accesso alle notifiche.
- `completa`: aggiunte solo in `app/src/completa/`.
- Il codice che usa il listener controlla `BuildConfig.HAS_MEDIA_LISTENER`.

## 4. Verifica
1. `bash .claude/hooks/wait_setup.sh android` (SDK pronto?).
2. `./gradlew testStandardDebugUnitTest testCompletaDebugUnitTest` (lo standard parte
   anche dall'hook a fine turno).
3. Se tocchi manifest o risorse: `./gradlew assembleStandardDebug assembleCompletaDebug`.
4. Il comportamento reale (servizio, notifiche, avvio app) si verifica solo sul
   telefono: scrivi all'utente cosa controllare dopo l'installazione.

Testi dell'interfaccia in italiano in `res/values/strings.xml`.
