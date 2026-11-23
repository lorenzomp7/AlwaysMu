#!/usr/bin/env python3
"""Controlli sugli APK prima di pubblicare una release.

Due comandi, usati da .github/workflows/build-apk.yml:

  firma       Confronta l'impronta SHA-256 del certificato di ogni APK (letta con
              `rea inspect-apk-signature --json`) con quella di app/debug.keystore.
              Con una firma diversa il telefono rifiuta l'aggiornamento e bisogna
              disinstallare l'app, perdendo le impostazioni.

  componenti  Confronta i componenti esportati del manifest unito (generato da
              Gradle) con l'elenco consentito in scripts/componenti-esportati.json.
              Un componente esportato per sbaglio è raggiungibile da qualunque app.

Solo libreria standard di Python: gira sul runner di GitHub e in locale.
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
SCHEMES = ("v1", "v2", "v3", "v3.1")
COMPONENT_TAGS = ("activity", "activity-alias", "service", "receiver", "provider")


# ---------------------------------------------------------------- firma

def normalize_fingerprint(value: str) -> str:
    """'4B:0F:…' (keytool) o '4b0f…' (REA) → '4b0f…'."""
    return value.replace(":", "").strip().lower()


def signer_fingerprints(evidence: dict) -> set[str]:
    """Impronte SHA-256 dei certificati dei firmatari in un risultato REA."""
    schemes = evidence.get("normalized_result", {}).get("schemes", {})
    found: set[str] = set()
    for name in SCHEMES:
        scheme = schemes.get(name) or {}
        if not scheme.get("present"):
            continue
        for signer in scheme.get("signers") or []:
            cert = signer.get("certificate") or {}
            fp = cert.get("sha256_fingerprint")
            if fp:
                found.add(normalize_fingerprint(fp))
    return found


def check_signature(label: str, evidence: dict, expected: str) -> list[str]:
    """Errori (vuoto = tutto a posto) per un APK rispetto all'impronta attesa."""
    errors: list[str] = []
    posture = evidence.get("normalized_result", {}).get("posture") or {}
    if posture.get("self_consistent") is not True:
        errors.append(f"{label}: i digest della firma non corrispondono al contenuto dell'APK")
    if posture.get("schemes_agree_on_signer") is False:
        errors.append(f"{label}: gli schemi di firma indicano firmatari diversi")
    found = signer_fingerprints(evidence)
    if not found:
        errors.append(f"{label}: nessun certificato di firma trovato")
    elif found != {expected}:
        errors.append(
            f"{label}: firmato con {', '.join(sorted(found))}, atteso {expected} (app/debug.keystore)"
        )
    return errors


# ---------------------------------------------------------- componenti

def exported_components(manifest_xml: str, package: str) -> set[str]:
    """Nomi completi dei componenti esportati, letti dal manifest unito."""
    root = ET.fromstring(manifest_xml)
    pkg = root.get("package") or package
    app = root.find("application")
    if app is None:
        return set()
    result: set[str] = set()
    for tag in COMPONENT_TAGS:
        for node in app.findall(tag):
            exported = node.get(ANDROID_NS + "exported")
            # Con targetSdk 31+ "exported" è obbligatorio dove ci sono intent-filter;
            # senza attributo e senza filtri il componente non è esportato.
            if exported is None:
                is_exported = node.find("intent-filter") is not None
            else:
                is_exported = exported.strip().lower() == "true"
            if not is_exported:
                continue
            name = node.get(ANDROID_NS + "name") or ""
            if name.startswith("."):
                name = pkg + name
            elif "." not in name:
                name = f"{pkg}.{name}"
            result.add(name)
    return result


def check_components(flavor: str, found: set[str], allowed: dict) -> list[str]:
    expected = set(allowed.get(flavor, []))
    errors: list[str] = []
    for name in sorted(found - expected):
        errors.append(f"{flavor}: {name} è esportato ma non è nell'elenco consentito")
    for name in sorted(expected - found):
        errors.append(f"{flavor}: {name} dovrebbe essere esportato ma non lo è più")
    return errors


# ------------------------------------------------------------------ CLI

def _load_json(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def cmd_firma(args: argparse.Namespace) -> int:
    expected = normalize_fingerprint(args.atteso)
    errors: list[str] = []
    for path in args.evidenze:
        errors += check_signature(Path(path).stem, _load_json(path), expected)
    for line in errors:
        print(f"::error::{line}")
    if not errors:
        print(f"Firma OK: {len(args.evidenze)} APK firmati con {expected}")
    return 1 if errors else 0


def cmd_componenti(args: argparse.Namespace) -> int:
    allowed = _load_json(args.consentiti)
    errors: list[str] = []
    for item in args.manifest:
        flavor, _, path = item.partition("=")
        if not flavor or not path or not Path(path).is_file():
            print(f"::error::manifest unito non trovato per «{item}» (formato flavor=percorso)")
            return 2
        found = exported_components(Path(path).read_text(encoding="utf-8"), args.package)
        errors += check_components(flavor, found, allowed)
        print(f"{flavor}: esportati {', '.join(sorted(found)) or 'nessuno'}")
    for line in errors:
        print(f"::error::{line}")
    return 1 if errors else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="comando", required=True)

    p = sub.add_parser("firma", help="impronta del certificato degli APK")
    p.add_argument("--atteso", required=True, help="SHA-256 del certificato in app/debug.keystore")
    p.add_argument("evidenze", nargs="+", help="file JSON di rea inspect-apk-signature")
    p.set_defaults(func=cmd_firma)

    p = sub.add_parser("componenti", help="componenti esportati del manifest unito")
    p.add_argument("--consentiti", default="scripts/componenti-esportati.json")
    p.add_argument("--package", default="com.alwaysmu.app")
    p.add_argument("manifest", nargs="+", help="flavor=percorso/AndroidManifest.xml")
    p.set_defaults(func=cmd_componenti)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
