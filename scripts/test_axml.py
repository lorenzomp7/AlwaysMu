"""Test di scripts/axml.py e della modalità --apk: python3 -m unittest discover -s scripts"""

import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import audit_app_musicali as a
import axml

MANIFEST = (Path(__file__).parent / "test_fixtures" / "alwaysmu-1.0.11-AndroidManifest.xml").read_bytes()


def apk_bytes() -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("AndroidManifest.xml", MANIFEST)
        z.writestr("classes.dex", b"dex")
    return buf.getvalue()


class AxmlTest(unittest.TestCase):
    def test_manifest_reale_di_alwaysmu(self):
        r = axml.parse_manifest(MANIFEST)
        self.assertEqual(r["package"], "com.alwaysmu.app")
        items = {i["name"]: i for i in r["components"]["items"]}
        self.assertTrue(items["com.alwaysmu.app.MainActivity"]["exported"])
        self.assertFalse(items["com.alwaysmu.app.MusicWatchdogService"]["exported"])
        self.assertIn("android.intent.action.BOOT_COMPLETED",
                      items["com.alwaysmu.app.BootReceiver"]["actions"])
        self.assertEqual(items["androidx.profileinstaller.ProfileInstallReceiver"]["permission"],
                         "android.permission.DUMP")

    def test_pacchetto_diviso_usa_base_apk(self):
        with tempfile.TemporaryDirectory() as d:
            bundle = Path(d) / "app.apks"
            with zipfile.ZipFile(bundle, "w") as z:
                z.writestr("split_config.it.apk", b"PK-non-valido")
                z.writestr("base.apk", apk_bytes())
            r = axml.inspect_apk(bundle)["normalized_result"]
            self.assertEqual(r["package"], "com.alwaysmu.app")

    def test_file_senza_manifest(self):
        with tempfile.TemporaryDirectory() as d:
            bad = Path(d) / "x.apk"
            with zipfile.ZipFile(bad, "w") as z:
                z.writestr("readme.txt", "x")
            with self.assertRaises(ValueError):
                axml.inspect_apk(bad)


class ModalitaApkTest(unittest.TestCase):
    def test_righe_per_il_catalogo(self):
        catalog = [("Spotify", ["com.spotify.music"]), ("Tidal", ["com.aspiro.tidal"])]
        ev = {"normalized_result": {"components": {"items": [
            {"kind": "service", "name": "x.S", "exported": True,
             "actions": ["android.media.browse.MediaBrowserService"]}]}}}
        rows = a.rows_from_apks(catalog, {"com.spotify.music": ev})
        self.assertTrue(rows[0]["manifest"]["media_browser_service"])
        self.assertEqual(rows[1]["assente"], "APK non fornito")
        self.assertIn("| Tidal | com.aspiro.tidal | APK non fornito |", a.markdown_rows(rows))

    def test_cartella_di_apk_con_motore_interno(self):
        with tempfile.TemporaryDirectory() as d:
            (Path(d) / "in").mkdir()
            (Path(d) / "in" / "alwaysmu.apk").write_bytes(apk_bytes())
            self.assertEqual(a.audit_apks([str(Path(d) / "in")], "interno", Path(d) / "out"), 0)
            summary = json.loads(next((Path(d) / "out").glob("*/riepilogo.json")).read_text())
            self.assertEqual(summary["fuori_catalogo"], ["com.alwaysmu.app"])
            self.assertEqual(len(summary["app"]), 13)


if __name__ == "__main__":
    unittest.main()
