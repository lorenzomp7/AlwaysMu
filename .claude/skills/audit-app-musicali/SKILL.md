---
name: audit-app-musicali
description: Analizza gli APK delle app musicali del catalogo (dati da Lorenzo in chat o in una cartella del PC) e compila docs/compatibilita.md. Usala quando l'utente manda o indica APK di app musicali, chiede l'audit o la compatibilità delle app musicali, o di usare REA. Non serve il telefono.
---

# Audit delle app musicali dagli APK

L'utente fa tutto dalla chat: ti dà gli APK (allegati in chat o una cartella sul suo PC)
e tu esegui ogni comando. Il telefono **non** serve: non chiederlo.

## 1. Trova gli APK
- In chat (Claude Code nel cloud o chat Claude): gli allegati sono in `/mnt/user-data/uploads/`.
- Sul PC: la cartella che indica l'utente. Formati: `.apk`, `.apks`, `.xapk`, `.apkm`
  (per i pacchetti divisi si usa il `base.apk` interno).
- Copia gli APK fuori dalla repo; non vanno mai committati (sono di terzi).

## 2. Analizza
- Gli strumenti MCP di REA (server `rea`, da `.mcp.json`) sono disponibili in chat: per un
  singolo APK puoi chiamare direttamente `inspect_apk_signature` o `inspect_android_package`.
  Per la tabella completa usa lo script, che dà lo stesso riepilogo per tutte le app:
- `python3 scripts/audit_app_musicali.py --apk <file o cartelle>`
  (`python` su Windows). Scrive `audit-app-musicali/<data>/riepilogo.md` e `riepilogo.json`.
- Motore: `--motore auto` usa REA (`rea inspect-android-package`) se `rea` è installato e
  `REA_JADX_MCP_JAR` è impostato, altrimenti il lettore interno `scripts/axml.py`, che legge
  il manifest binario senza programmi esterni. Nel cloud REA è pronto dopo
  `bash .claude/rea/installa.sh --attendi` (lo avvia l'hook SessionStart; skill `rea`).
- Sul PC Windows, se l'utente vuole REA e non c'è:
  `powershell -ExecutionPolicy Bypass -File .claude\rea\installa-windows.ps1 -SenzaDomande` (0 €).
- APK che danno errore: dillo all'utente per nome (spesso un `.apkm` cifrato); gli altri
  vanno avanti. Pacchetti fuori catalogo sono elencati in `fuori_catalogo`: chiedi se vanno
  aggiunti a `MusicApp.KNOWN` (cambio di codice: skill `watchdog-change`).

## 3. Tabella e commit
- Copia le righe di `riepilogo.md` nella tabella «Dichiarato nel manifest» di
  `docs/compatibilita.md` e aggiorna «Stato» con data, versioni degli APK se note e motore.
- La colonna «Sessione» resta «non provata»: si riempie solo con un telefono collegato
  (`--sessione`), che l'utente non ha chiesto.
- Non scrivere nulla in «Provato sul telefono» che l'utente non abbia visto succedere.
- Proponi l'ordine iniziale per `MethodRanker` nella sezione dedicata, motivato dai dati.
- Commit a nome di Lorenzo (vedi AGENTS.md) con `[skip ci]`, push su `main_claude`.
