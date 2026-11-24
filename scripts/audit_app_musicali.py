#!/usr/bin/env python3
r"""Cosa offre ogni app musicale del catalogo: manifest e sessione multimediale.

Dagli APK (senza telefono): file .apk, .apks, .xapk o .apkm, o una cartella che li contiene:

    python scripts/audit_app_musicali.py --apk C:\Users\lorenzo\Downloads\apk

Con il telefono collegato via USB (debug USB attivo), facoltativo:

    python scripts/audit_app_musicali.py            # solo manifest
    python scripts/audit_app_musicali.py --sessioni # anche dumpsys durante la riproduzione
    python scripts/audit_app_musicali.py --sessione com.spotify.music  # una sessione, senza domande

Per ogni app di MusicApp.KNOWN installata sul telefono:
  1. `rea pull-adb-package` scarica il suo APK (REA di morluto, licenza MIT);
  2. `rea inspect-android-package` legge il manifest (serve JADX, vedi --help);
  3. con --sessioni chiede di far partire la musica e legge `adb shell dumpsys media_session`.

Si leggono solo le dichiarazioni dei manifest per interoperabilità: nessun codice delle
app viene copiato. Il risultato va in audit-app-musicali/<data>/ (riepilogo.json e
riepilogo.md); è quello che serve per completare docs/compatibilita.md.

Prerequisiti (gratuiti): Node.js 22+, `npm install --global rea-agents@6.10.1`, adb
(Android platform-tools), JDK 17+ e il JAR di JADX indicato in JADX_HELP.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

import axml

REPO = Path(__file__).resolve().parent.parent
MUSIC_APP_KT = REPO / "app/src/main/java/com/alwaysmu/app/MusicApp.kt"

JADX_HELP = (
    "Per leggere i manifest REA usa JADX: scarica "
    "https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar, "
    "controlla che lo SHA-256 sia 6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75 "
    "e imposta REA_JADX_MCP_JAR con il suo percorso assoluto."
)

# Azioni degli intent che interessano ad AlwaysMu (punti 8, 13 e 21 del Piano).
ACTION_MEDIA_BROWSER = "android.media.browse.MediaBrowserService"
ACTION_MEDIA3_SESSION = "androidx.media3.session.MediaSessionService"
ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON"
ACTION_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"

# PlaybackState.ACTION_* (android.media.session.PlaybackState).
PLAYBACK_ACTIONS = [
    (1 << 0, "STOP"), (1 << 1, "PAUSE"), (1 << 2, "PLAY"), (1 << 3, "REWIND"),
    (1 << 4, "SKIP_TO_PREVIOUS"), (1 << 5, "SKIP_TO_NEXT"), (1 << 6, "FAST_FORWARD"),
    (1 << 7, "SET_RATING"), (1 << 8, "SEEK_TO"), (1 << 9, "PLAY_PAUSE"),
    (1 << 10, "PLAY_FROM_MEDIA_ID"), (1 << 11, "PLAY_FROM_SEARCH"),
    (1 << 12, "SKIP_TO_QUEUE_ITEM"), (1 << 13, "PLAY_FROM_URI"), (1 << 14, "PREPARE"),
    (1 << 15, "PREPARE_FROM_MEDIA_ID"), (1 << 16, "PREPARE_FROM_SEARCH"),
    (1 << 17, "PREPARE_FROM_URI"), (1 << 22, "SET_PLAYBACK_SPEED"),
]
PLAYBACK_STATES = {
    0: "NONE", 1: "STOPPED", 2: "PAUSED", 3: "PLAYING", 4: "FAST_FORWARDING",
    5: "REWINDING", 6: "BUFFERING", 7: "ERROR", 8: "CONNECTING",
    9: "SKIPPING_TO_PREVIOUS", 10: "SKIPPING_TO_NEXT", 11: "SKIPPING_TO_QUEUE_ITEM",
}


# ------------------------------------------------------------ logica pura

def parse_catalog(kotlin_source: str) -> list[tuple[str, list[str]]]:
    """(nome, pacchetti) dalle righe MusicApp("…", listOf("…")) di MusicApp.kt."""
    entries = []
    for label, pkgs in re.findall(r'MusicApp\("([^"]+)",\s*listOf\(([^)]*)\)\)', kotlin_source):
        entries.append((label, re.findall(r'"([^"]+)"', pkgs)))
    return entries


def _component_items(evidence: dict) -> list[dict]:
    comps = evidence.get("normalized_result", {}).get("components") or {}
    return comps.get("items") or []


def _is_exported(item: dict) -> bool | None:
    if item.get("exported") is not None:
        return bool(item["exported"])
    if item.get("exported_default") is not None:
        return bool(item["exported_default"])
    return None


def summarize_components(evidence: dict) -> dict:
    """Dal risultato di `rea inspect-android-package` le dichiarazioni che servono."""

    def pick(kind: str, action: str) -> list[dict]:
        out = []
        for item in _component_items(evidence):
            if item.get("kind") == kind and action in (item.get("actions") or []):
                out.append({
                    "nome": item.get("name") or item.get("declared_name"),
                    "esportato": _is_exported(item),
                    "permesso": item.get("permission"),
                })
        return out

    items = _component_items(evidence)
    comps = evidence.get("normalized_result", {}).get("components") or {}
    return {
        "media_browser_service": pick("service", ACTION_MEDIA_BROWSER),
        "media3_session_service": pick("service", ACTION_MEDIA3_SESSION),
        "ricevitori_media_button": pick("receiver", ACTION_MEDIA_BUTTON),
        "attivita_play_from_search": pick("activity", ACTION_PLAY_FROM_SEARCH)
        + pick("activity-alias", ACTION_PLAY_FROM_SEARCH),
        "servizi_esportati": sorted(
            str(i.get("name")) for i in items if i.get("kind") == "service" and _is_exported(i)
        ),
        "copertura_manifest": comps.get("status"),
    }


def decode_actions(mask: int) -> list[str]:
    return [name for bit, name in PLAYBACK_ACTIONS if mask & bit]


_SESSION_HEADER = re.compile(r"^\s+(.+?) (\S+?)/(.+?) \(userId=\d+\)\s*$")


def parse_media_sessions(dumpsys: str, package: str) -> list[dict]:
    """Sessioni di `package` nell'output di `adb shell dumpsys media_session`."""
    sessions: list[dict] = []
    current: dict | None = None
    for line in dumpsys.splitlines():
        header = _SESSION_HEADER.match(line)
        if header:
            current = {"tag": header.group(1), "pacchetto": header.group(2)}
            sessions.append(current)
            continue
        if current is None:
            continue
        text = line.strip()
        if text.startswith("package="):
            current["pacchetto"] = text.split("=", 1)[1]
        elif text.startswith("active="):
            current["attiva"] = text.split("=", 1)[1] == "true"
        elif text.startswith("mediaButtonReceiver="):
            current["media_button_receiver"] = text.split("=", 1)[1]
        elif text.startswith("state=PlaybackState"):
            state = re.search(r"\bstate=(\d+)", text[len("state="):])
            actions = re.search(r"\bactions=(\d+)", text)
            if state:
                current["stato"] = PLAYBACK_STATES.get(int(state.group(1)), state.group(1))
            if actions:
                current["azioni"] = decode_actions(int(actions.group(1)))
    return [s for s in sessions if s.get("pacchetto") == package]


def merge_sessions(summary: dict, package: str, sessions: list[dict]) -> bool:
    """Mette in `summary` le sessioni lette per `package`; False se l'app non c'è."""
    for row in summary.get("app", []):
        if row.get("pacchetto") == package:
            row["sessioni"] = sessions
            return True
    return False


def latest_run(base: Path) -> Path | None:
    runs = sorted(p for p in base.glob("*/riepilogo.json") if p.is_file())
    return runs[-1] if runs else None


def markdown_rows(results: list[dict]) -> str:
    """Righe per la parte «dichiarato nel manifest» di docs/compatibilita.md."""

    def yes(values: list) -> str:
        if not values:
            return "no"
        flags = {v.get("esportato") for v in values}
        return "sì" if True in flags else "sì (non esportato)"

    lines = [
        "| App | Pacchetto | MediaBrowserService | Media3 session | MEDIA_BUTTON | PLAY_FROM_SEARCH | Sessione (azioni) |",
        "|---|---|---|---|---|---|---|",
    ]
    for r in results:
        if not r.get("installata"):
            lines.append(f"| {r['app']} | {r['pacchetto']} | {r.get('assente', 'non installata')} | | | | |")
            continue
        m = r.get("manifest")
        if not m:
            cells = ["errore: " + str(r.get("errore_manifest", "?"))[:60], "", "", ""]
        else:
            cells = [
                yes(m["media_browser_service"]),
                yes(m["media3_session_service"]),
                yes(m["ricevitori_media_button"]),
                yes(m["attivita_play_from_search"]),
            ]
        sess = r.get("sessioni")
        if sess is None:
            s = "non provata"
        elif not sess:
            s = "nessuna sessione"
        else:
            s = "; ".join(f"{x.get('stato', '?')}: {', '.join(x.get('azioni', []))}" for x in sess)
        lines.append(f"| {r['app']} | {r['pacchetto']} | " + " | ".join(cells) + f" | {s} |")
    return "\n".join(lines) + "\n"


# ------------------------------------------------------ chiamate esterne

def run(cmd: list[str], timeout: int = 300) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8",
                          errors="replace", timeout=timeout)


def pick_serial(adb: str, wanted: str | None) -> str:
    out = run([adb, "devices"]).stdout
    devices = [l.split()[0] for l in out.splitlines()[1:] if l.strip().endswith("device")]
    if wanted:
        if wanted not in devices:
            sys.exit(f"Il dispositivo {wanted} non è collegato (trovati: {devices or 'nessuno'}).")
        return wanted
    if len(devices) != 1:
        sys.exit(f"Collega un solo telefono o usa --serial (trovati: {devices or 'nessuno'}).")
    return devices[0]


def installed(adb: str, serial: str, pkg: str) -> bool:
    return run([adb, "-s", serial, "shell", "pm", "path", pkg]).stdout.strip().startswith("package:")


def audit_manifest(rea: str, serial: str, pkg: str, out: Path) -> dict:
    apk_dir = out / "apk"
    pulled = run([rea, "pull-adb-package", serial, pkg, "--output-directory", str(apk_dir), "--json"])
    (out / "rea" / f"{pkg}.pull.json").write_text(pulled.stdout, encoding="utf-8")
    base = apk_dir / pkg / "base.apk"
    if pulled.returncode != 0 or not base.is_file():
        return {"errore_manifest": "pull-adb-package: " + (pulled.stdout or pulled.stderr)[:300]}
    inspected = run([rea, "inspect-android-package", str(base.resolve()), "--json"], timeout=900)
    (out / "rea" / f"{pkg}.manifest.json").write_text(inspected.stdout, encoding="utf-8")
    try:
        evidence = json.loads(inspected.stdout)
    except json.JSONDecodeError:
        return {"errore_manifest": (inspected.stderr or inspected.stdout)[:300]}
    if inspected.returncode != 0 or "normalized_result" not in evidence:
        return {"errore_manifest": evidence.get("message", "inspect-android-package fallito")[:300]}
    return {"manifest": summarize_components(evidence)}


APK_SUFFIXES = (".apk", ".apks", ".xapk", ".apkm")


def collect_apks(paths: list[str]) -> list[Path]:
    found: list[Path] = []
    for raw in paths:
        p = Path(raw)
        if p.is_dir():
            found += sorted(f for f in p.rglob("*") if f.suffix.lower() in APK_SUFFIXES)
        elif p.is_file():
            found.append(p)
        else:
            print(f"Attenzione: {raw} non esiste")
    return found


def inspect_with_rea(rea: str, apk: Path) -> dict:
    done = run([rea, "inspect-android-package", str(apk.resolve()), "--json"], timeout=900)
    evidence = json.loads(done.stdout)
    if done.returncode != 0 or "normalized_result" not in evidence:
        raise RuntimeError(evidence.get("message", "inspect-android-package fallito"))
    return evidence


def rows_from_apks(catalog: list[tuple[str, list[str]]], evidences: dict[str, dict]) -> list[dict]:
    """Una riga per app del catalogo; `evidences` è pacchetto → risultato del manifest."""
    rows = []
    for label, packages in catalog:
        pkg = next((p for p in packages if p in evidences), packages[0])
        if pkg in evidences:
            rows.append({"app": label, "pacchetto": pkg, "installata": True,
                         "manifest": summarize_components(evidences[pkg])})
        else:
            rows.append({"app": label, "pacchetto": pkg, "installata": False,
                         "assente": "APK non fornito"})
    return rows


def audit_apks(paths: list[str], engine: str, base: Path) -> int:
    rea = shutil.which("rea")
    if engine == "auto":
        engine = "rea" if rea and os.environ.get("REA_JADX_MCP_JAR") else "interno"
    if engine == "rea" and not rea:
        sys.exit("rea non trovato: npm install --global rea-agents@6.10.1")
    apks = collect_apks(paths)
    if not apks:
        sys.exit("Nessun APK trovato (.apk, .apks, .xapk, .apkm).")
    out = base / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    (out / "rea").mkdir(parents=True)
    evidences: dict[str, dict] = {}
    for apk in apks:
        try:
            ev = inspect_with_rea(rea, apk) if engine == "rea" else axml.inspect_apk(apk)
        except Exception as exc:  # un APK rotto non ferma gli altri
            print(f"- {apk.name}: errore {exc}")
            continue
        n = ev.get("normalized_result", {})
        pkg = n.get("package") or n.get("package_name") or apk.stem
        (out / "rea" / f"{pkg}.manifest.json").write_text(json.dumps(ev, indent=2), encoding="utf-8")
        evidences[pkg] = ev
        print(f"- {apk.name}: {pkg}")
    catalog = parse_catalog(MUSIC_APP_KT.read_text(encoding="utf-8"))
    known = {p for _, ps in catalog for p in ps}
    extra = sorted(p for p in evidences if p not in known)
    rows = rows_from_apks(catalog, evidences)
    summary = {"fonte": f"APK ({engine})", "data": dt.datetime.now().isoformat(timespec="seconds"),
               "fuori_catalogo": extra, "app": rows}
    (out / "riepilogo.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    (out / "riepilogo.md").write_text(markdown_rows(rows), encoding="utf-8")
    if extra:
        print(f"Fuori catalogo (ignorati): {', '.join(extra)}")
    print(f"\nFatto ({engine}): {out / 'riepilogo.md'}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0], epilog=JADX_HELP)
    parser.add_argument("--serial", help="dispositivo come appare in `adb devices`")
    parser.add_argument("--sessioni", action="store_true",
                        help="per ogni app chiede di far partire la musica e legge dumpsys media_session")
    parser.add_argument("--sessione", metavar="PACCHETTO",
                        help="senza domande: legge ora dumpsys media_session per un'app e lo aggiunge "
                             "all'ultimo riepilogo (la musica deve già suonare in quell'app)")
    parser.add_argument("--apk", nargs="+", metavar="PERCORSO",
                        help="analizza questi APK o cartelle di APK, senza telefono")
    parser.add_argument("--motore", choices=("auto", "rea", "interno"), default="auto",
                        help="con --apk: REA (serve JADX) o il lettore interno; auto = REA se pronto")
    parser.add_argument("--uscita", default=str(REPO / "audit-app-musicali"))
    args = parser.parse_args(argv)
    if args.apk:
        return audit_apks(args.apk, args.motore, Path(args.uscita))

    adb = os.environ.get("REA_ADB_PATH") or shutil.which("adb")
    rea = shutil.which("rea")
    if not adb:
        sys.exit("adb non trovato: installa le Android platform-tools o imposta REA_ADB_PATH.")
    if args.sessione:
        return capture_one_session(adb, pick_serial(adb, args.serial), args.sessione, Path(args.uscita))
    if not rea:
        sys.exit("rea non trovato: npm install --global rea-agents@6.10.1")
    if not os.environ.get("REA_JADX_MCP_JAR"):
        print("Attenzione: REA_JADX_MCP_JAR non è impostato, i manifest non verranno letti.\n" + JADX_HELP)

    serial = pick_serial(adb, args.serial)
    model = run([adb, "-s", serial, "shell", "getprop", "ro.product.model"]).stdout.strip()
    sdk = run([adb, "-s", serial, "shell", "getprop", "ro.build.version.sdk"]).stdout.strip()
    out = Path(args.uscita) / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    (out / "rea").mkdir(parents=True)
    print(f"Telefono {model} (API {sdk}), risultati in {out}")

    results = []
    for label, packages in parse_catalog(MUSIC_APP_KT.read_text(encoding="utf-8")):
        pkg = next((p for p in packages if installed(adb, serial, p)), packages[0])
        row: dict = {"app": label, "pacchetto": pkg, "installata": installed(adb, serial, pkg)}
        if row["installata"]:
            print(f"- {label}: leggo il manifest…")
            row.update(audit_manifest(rea, serial, pkg, out))
            if args.sessioni:
                answer = input(f"  Fai partire la musica in {label} e premi Invio (s = salta): ")
                if answer.strip().lower() != "s":
                    dump = run([adb, "-s", serial, "shell", "dumpsys", "media_session"]).stdout
                    (out / "rea" / f"{pkg}.media_session.txt").write_text(dump, encoding="utf-8")
                    row["sessioni"] = parse_media_sessions(dump, pkg)
        else:
            print(f"- {label}: non installata")
        results.append(row)

    summary = {"telefono": model, "api": sdk, "data": dt.datetime.now().isoformat(timespec="seconds"),
               "app": results}
    (out / "riepilogo.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    (out / "riepilogo.md").write_text(markdown_rows(results), encoding="utf-8")
    print(f"\nFatto: {out / 'riepilogo.md'}")
    return 0


def capture_one_session(adb: str, serial: str, pkg: str, base: Path) -> int:
    summary_path = latest_run(base)
    if summary_path is None:
        sys.exit("Nessun riepilogo: lancia prima lo script senza --sessione.")
    dump = run([adb, "-s", serial, "shell", "dumpsys", "media_session"]).stdout
    (summary_path.parent / "rea").mkdir(exist_ok=True)
    (summary_path.parent / "rea" / f"{pkg}.media_session.txt").write_text(dump, encoding="utf-8")
    sessions = parse_media_sessions(dump, pkg)
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    if not merge_sessions(summary, pkg, sessions):
        sys.exit(f"{pkg} non è nel riepilogo {summary_path}.")
    summary_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    (summary_path.parent / "riepilogo.md").write_text(markdown_rows(summary["app"]), encoding="utf-8")
    print(json.dumps({"pacchetto": pkg, "sessioni": sessions}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
