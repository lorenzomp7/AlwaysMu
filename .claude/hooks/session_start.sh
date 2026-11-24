#!/usr/bin/env bash
# Hook SessionStart: prepara l'ambiente a ogni avvio di una sessione Claude Code cloud.
# Fuori dal cloud non fa nulla (vedi in_cloud in session_lib.sh).
source "${CLAUDE_PROJECT_DIR:-.}/.claude/hooks/session_lib.sh"
in_cloud || exit 0
cd "$ROOT" || exit 0

# Android: SDK platform 35 e build-tools (richiede dl.google.com). Java 21 è già nell'ambiente.
export_env 'ANDROID_HOME="$HOME/android-sdk"'
export_env 'ANDROID_SDK_ROOT="$HOME/android-sdk"'
export_env 'PATH="$HOME/android-sdk/cmdline-tools/latest/bin:$HOME/android-sdk/platform-tools:$PATH"'
background android .claude/hooks/android_sdk.sh "platforms;android-35" "build-tools;35.0.0" platform-tools
note "Verifica standard (quando Android è pronto): ./gradlew testStandardDebugUnitTest"
# REA (analisi degli APK dalla chat): JADX, Apktool e REA in ~/.cache/rea-tools, in background.
note "$(bash "$ROOT/.claude/rea/installa.sh" --sfondo 2>&1 | tail -n 1). Prima di analizzare APK: bash .claude/rea/installa.sh --attendi (skill rea)"
REA_DIR="${REA_TOOLS_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/rea-tools}"
export_env "PATH=\"$REA_DIR/bin:\$PATH\""
export_env "REA_JADX_MCP_JAR=\"$REA_DIR/jadx-headless-mcp-0.7.1-all.jar\""
export_env "REA_APKTOOL_COMMAND=\"$REA_DIR/apktool\""
finish
