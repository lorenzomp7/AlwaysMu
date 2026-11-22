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
finish
