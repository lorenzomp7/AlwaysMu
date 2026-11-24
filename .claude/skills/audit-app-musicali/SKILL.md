---
name: audit-app-musicali
description: Analizza con REA le app musicali del catalogo sul telefono di Lorenzo (manifest e sessioni multimediali) e compila docs/compatibilita.md. Usala quando l'utente chiede l'audit o la compatibilità delle app musicali, di usare REA, o di riempire la tabella di compatibilità. Funziona solo in Claude Code sul PC Windows con il telefono collegato, non nel cloud.
---

# Audit delle app musicali con REA

L'utente vuole fare tutto dalla chat: esegui tu ogni comando, chiedigli solo le azioni
fisiche sul telefono (collegarlo, accettare il debug USB, far partire la musica).

## 1. Dove sei
- Se `uname` risponde Linux e non c'è un telefono raggiungibile, sei nel cloud: spiega
  che l'audit va lanciato da Claude Code sul PC (cartella della repo, branch
  `main_claude`) o da una chat dell'app desktop collegata al PC, e fermati.

## 2. Prerequisiti (una volta)
- `rea doctor --client claude_code --provider jadx` (in PowerShell). Se `rea` manca o il
  controllo non è sano:
  `powershell -ExecutionPolicy Bypass -File scripts\installa-rea.ps1 -SenzaDomande`
  Installa Node.js, adb, JDK, JADX (SHA-256 controllato) e rea-agents@6.10.1 e registra REA
  in Claude Code (e in Claude Desktop se è chiuso). Costo 0 €.
- Le variabili `REA_ADB_PATH` e `REA_JADX_MCP_JAR` valgono per i processi nuovi: nella
  stessa sessione rileggile con
  `[Environment]::GetEnvironmentVariable('REA_JADX_MCP_JAR','User')` e passale ai comandi.
- Gli strumenti MCP di REA compaiono solo dopo il riavvio di Claude Code. Non serve
  aspettarli: lo script qui sotto usa la riga di comando `rea`.

## 3. Telefono
- `adb devices`: se è vuoto o `unauthorized`, chiedi all'utente di collegare il telefono
  con il debug USB attivo e di accettare l'impronta del PC, poi ricontrolla.

## 4. Manifest (senza domande)
- `python scripts/audit_app_musicali.py` → `audit-app-musicali/<data>/riepilogo.md` e
  `riepilogo.json`. Se un'app dà `errore_manifest`, leggi `rea/<pacchetto>.manifest.json`
  nella stessa cartella e correggi lo script (con test) prima di andare avanti.

## 5. Sessioni (una app alla volta)
- Per ogni app installata: chiedi all'utente di far partire la musica in quell'app, poi
  `python scripts/audit_app_musicali.py --sessione <pacchetto>`. Se l'utente salta un'app,
  lasciala «non provata».

## 6. Tabella e commit
- Copia le righe di `riepilogo.md` nella tabella «Dichiarato nel manifest» di
  `docs/compatibilita.md`; aggiorna «Stato» con data, telefono e API.
- Non scrivere nulla in «Provato sul telefono» che l'utente non abbia visto succedere.
- Proponi l'ordine iniziale per `MethodRanker` nella sezione dedicata, motivato dai dati.
- Commit a nome di Lorenzo (vedi AGENTS.md) con `[skip ci]`, push su `main_claude`.
- `audit-app-musicali/` non va mai committata: contiene APK di terzi.
