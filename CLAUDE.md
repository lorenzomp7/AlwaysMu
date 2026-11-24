@AGENTS.md

## Istruzioni specifiche per Claude Code

AGENTS.md (importato sopra) vale per tutti gli agenti; qui solo ciò che riguarda Claude Code.

### Metodo di lavoro

- Un compito per sessione. Tra un compito e il successivo usa `/clear`.
- Modifiche su più file o di architettura: prima plan mode, fai approvare il piano, poi implementa.
- Prima di modificare leggi i file coinvolti e i test esistenti. Niente refactor non richiesti.
- Ogni cambio di comportamento va con un test; esegui la verifica standard di AGENTS.md.
- Alla fine riporta: cosa è cambiato, comandi eseguiti con esito, cosa resta da fare.
- Subagenti solo se l'utente lo chiede o per la revisione con `reviewer` prima di chiudere un compito grande.

### Ambiente (sessioni cloud)

- All'avvio di ogni sessione cloud `.claude/hooks/session_start.sh` avvia in background l'SDK Android in `~/android-sdk` (alcuni minuti; serve `dl.google.com` tra i domini consentiti dell'ambiente).
- Prima di qualunque comando Gradle esegui `bash .claude/hooks/wait_setup.sh android`: aspetta la fine dell'installazione o spiega perché è fallita. Se fallisce per la rete, dillo all'utente invece di aggirare il problema.
- Se qualcosa manca, i dettagli sono in `.claude/hooks/.state/session-setup.log`; per ripetere la preparazione: `CLAUDE_SETUP_FORCE=1 bash .claude/hooks/session_start.sh`.

### Hook automatici (`.claude/settings.json`)

- Gli hook partono tramite `.claude/hooks/run.sh`, che usa il primo Python 3.9+ disponibile (`python3`, `python` o `py`). Se Python manca vengono saltati senza bloccare.
- Dopo ogni modifica un controllo rapido del file (sintassi, nomi non definiti, JSON/YAML validi). Se segnala un errore, correggilo subito.
- Prima di chiudere il turno, se hai modificato codice e l'SDK Android è pronto, parte `./gradlew testStandardDebugUnitTest` (la prima volta può richiedere alcuni minuti). Se l'SDK non è pronto i test vengono saltati con un avviso: dillo all'utente invece di dichiararli passati. Se falliscono correggi, oppure spiega perché il problema non dipende dalle tue modifiche. Per disattivarli in una sessione: `CLAUDE_SKIP_STOP_TESTS=1`.

### Skill

- Di progetto: `watchdog-change` (rilevamento musica, avvio app, volume, intervalli, permessi), `apk-release` (come escono gli APK, prima di ogni push su `main` o `main_claude`) e `audit-app-musicali` (APK dati da Lorenzo → manifest → `docs/compatibilita.md`, senza telefono).
- Generali, da `addyosmani/agent-skills` (versione fissata, fonte in `.claude/references/README.md`): pianificazione (`planning-and-task-breakdown`), piccoli passi (`incremental-implementation`), test prima del codice (`test-driven-development`), revisione (`code-review-and-quality`), debug (`debugging-and-error-recovery`).

### Subagenti

- `reviewer` (`.claude/agents/reviewer.md`): revisione indipendente del diff con i controlli specifici di questo progetto. Usalo quando l'utente lo chiede o prima di chiudere un compito grande.

### Server MCP

- `rea` (REA di morluto, MIT) è dichiarato in `.mcp.json` e attivato in `.claude/settings.json`:
  in ogni sessione Claude Code su questa repo ci sono i suoi strumenti per APK
  (famiglie `android,apktool,artifact`: `inspect_apk_signature`, `inspect_android_package`, …).
  Parte con `npx -y rea-agents@6.10.1 mcp`. Gli strumenti che decompilano o leggono il manifest
  vogliono JADX (`REA_JADX_MCP_JAR`); se non c'è, per il manifest usa `scripts/axml.py`.
- Sul PC Windows di Lorenzo `scripts\installa-rea.ps1` installa JADX e aggiunge una
  registrazione locale (`cmd /c rea mcp`) che ha la precedenza su `.mcp.json`.
- Per l'audit delle app musicali usa la skill `audit-app-musicali`. GitHub è già disponibile tramite `gh`.
