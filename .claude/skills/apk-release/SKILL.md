---
name: apk-release
description: Come vengono pubblicati gli APK di AlwaysMu e come evitare release inutili o doppie. Usala prima di fare push o merge su main o main_claude, e quando l'utente chiede una nuova versione.
---

# Pubblicazione degli APK

## Come funziona
- `.github/workflows/build-apk.yml` parte a ogni push su `main_claude` o `main`:
  test unitari, poi APK `standard` e `completa` firmati con `app/debug.keystore`,
  poi una release GitHub `v1.0.<numero esecuzione>` marcata latest.
- Una sola release per commit: se il commit ha già una release (stesso commit spinto
  su entrambi i branch, o rilancio manuale) la pubblicazione viene saltata.
- Le esecuzioni che pubblicano vanno in fila (`concurrency: alwaysmu-release`).
- Prima di pubblicare, il passo «Controllo firma e componenti esportati» blocca la release se:
  - un APK nuovo o quello dell'ultima release non è firmato con il certificato di
    `app/debug.keystore` (letto con `rea inspect-apk-signature`, REA di morluto);
  - i componenti esportati del manifest unito non sono esattamente quelli di
    `scripts/componenti-esportati.json`.
  Il codice è in `scripts/check_release_apk.py`, con test in `scripts/test_check_release_apk.py`.
- Sulle pull request gira solo `.github/workflows/test.yml` (test di entrambi i flavor).

## Regole
- Un push su `main` o `main_claude` = una nuova release pubblica. Avvisa l'utente prima.
- Se il commit cambia solo file per agenti o documentazione (`.claude/`, `CLAUDE.md`,
  `AGENTS.md`, `*.md`), metti `[skip ci]` nel messaggio di commit: niente release identica.
- Non cambiare `app/debug.keystore`: con una firma diversa il telefono rifiuta
  l'aggiornamento e bisogna disinstallare l'app.
- `versionCode` e `versionName` vengono da `GITHUB_RUN_NUMBER` (`app/build.gradle.kts`):
  ogni build di GitHub Actions ha un numero più alto della precedente, quindi l'APK
  aggiorna sempre quello installato. Non sostituirli con numeri fissi; una build locale
  ha versione 1 e non aggiorna un APK scaricato dalle release.

## Prima di pubblicare
1. `./gradlew testStandardDebugUnitTest testCompletaDebugUnitTest` e
   `python3 -m unittest discover -s scripts` verdi.
   Se aggiungi un componente con `android:exported="true"` (o una libreria che lo porta),
   aggiornalo in `scripts/componenti-esportati.json` e spiega all'utente perché serve.
2. La pull request ha `Test` verde su GitHub.
3. Scrivi all'utente cosa cambia per chi usa l'app e quale APK scaricare
   (`AlwaysMu-1.0.N.apk` consigliato, `-completa` solo se serve l'accesso alle notifiche).
