"""Test di scripts/check_release_apk.py: python3 -m unittest discover -s scripts"""

import copy
import json
import unittest
from pathlib import Path

import check_release_apk as c

FIXTURES = Path(__file__).parent / "test_fixtures"
# Impronta SHA-256 del certificato in app/debug.keystore (keytool -list -v).
KEYSTORE_FP = "4B:0F:F5:5D:97:56:76:58:01:B0:0E:96:7E:1B:84:3B:98:37:78:B5:9D:32:30:25:55:89:E6:E2:D9:12:50:49"


def release_evidence() -> dict:
    return json.loads((FIXTURES / "rea-firma-1.0.11.json").read_text(encoding="utf-8"))


class FirmaTest(unittest.TestCase):
    def test_release_1_0_11_firmata_con_debug_keystore(self):
        errors = c.check_signature("1.0.11", release_evidence(), c.normalize_fingerprint(KEYSTORE_FP))
        self.assertEqual(errors, [])

    def test_formato_keytool_e_rea_si_equivalgono(self):
        self.assertEqual(
            c.normalize_fingerprint("4B:0F:F5"), c.normalize_fingerprint(" 4b0ff5 ")
        )

    def test_altra_chiave_fa_fallire(self):
        ev = release_evidence()
        ev["normalized_result"]["schemes"]["v2"]["signers"][0]["certificate"]["sha256_fingerprint"] = "ab" * 32
        errors = c.check_signature("nuovo", ev, c.normalize_fingerprint(KEYSTORE_FP))
        self.assertEqual(len(errors), 1)
        self.assertIn("atteso", errors[0])

    def test_due_firmatari_diversi_fanno_fallire(self):
        ev = release_evidence()
        schemes = ev["normalized_result"]["schemes"]
        v3 = copy.deepcopy(schemes["v2"])
        v3["signers"][0]["certificate"]["sha256_fingerprint"] = "cd" * 32
        schemes["v3"] = v3
        errors = c.check_signature("nuovo", ev, c.normalize_fingerprint(KEYSTORE_FP))
        self.assertTrue(any("firmato con" in e for e in errors))

    def test_digest_non_coerente_fa_fallire(self):
        ev = release_evidence()
        ev["normalized_result"]["posture"]["self_consistent"] = False
        errors = c.check_signature("nuovo", ev, c.normalize_fingerprint(KEYSTORE_FP))
        self.assertTrue(any("digest" in e for e in errors))

    def test_apk_non_firmato_fa_fallire(self):
        ev = release_evidence()
        for scheme in ev["normalized_result"]["schemes"].values():
            scheme["present"] = False
        errors = c.check_signature("nuovo", ev, c.normalize_fingerprint(KEYSTORE_FP))
        self.assertTrue(any("nessun certificato" in e for e in errors))


MERGED = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.alwaysmu.app">
  <application>
    <activity android:name="com.alwaysmu.app.MainActivity" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/></intent-filter>
    </activity>
    <service android:name="com.alwaysmu.app.MusicWatchdogService" android:exported="false"/>
    <receiver android:name=".BootReceiver" android:exported="true">
      <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED"/></intent-filter>
    </receiver>
    <provider android:name="androidx.startup.InitializationProvider" android:exported="false"/>
    <receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:exported="true"
        android:permission="android.permission.DUMP">
      <intent-filter><action android:name="androidx.profileinstaller.action.INSTALL_PROFILE"/></intent-filter>
    </receiver>
    {extra}
  </application>
</manifest>
"""

ALLOWED = json.loads((Path(__file__).parent / "componenti-esportati.json").read_text(encoding="utf-8"))


class ComponentiTest(unittest.TestCase):
    def test_manifest_standard_attuale_passa(self):
        found = c.exported_components(MERGED.format(extra=""), "com.alwaysmu.app")
        self.assertEqual(
            found,
            {
                "com.alwaysmu.app.MainActivity",
                "com.alwaysmu.app.BootReceiver",
                "androidx.profileinstaller.ProfileInstallReceiver",
            },
        )
        self.assertEqual(c.check_components("standard", found, ALLOWED), [])

    def test_servizio_esportato_in_piu_fa_fallire(self):
        extra = '<service android:name=".MediaListenerService" android:exported="true"/>'
        found = c.exported_components(MERGED.format(extra=extra), "com.alwaysmu.app")
        self.assertEqual(c.check_components("completa", found, ALLOWED), [])
        errors = c.check_components("standard", found, ALLOWED)
        self.assertEqual(len(errors), 1)
        self.assertIn("MediaListenerService", errors[0])

    def test_libreria_con_intent_filter_senza_exported_conta_come_esportata(self):
        extra = (
            '<receiver android:name="com.example.lib.SomeReceiver">'
            '<intent-filter><action android:name="x"/></intent-filter></receiver>'
        )
        found = c.exported_components(MERGED.format(extra=extra), "com.alwaysmu.app")
        self.assertIn("com.example.lib.SomeReceiver", found)
        self.assertEqual(len(c.check_components("standard", found, ALLOWED)), 1)

    def test_componente_sparito_viene_segnalato(self):
        found = {"com.alwaysmu.app.MainActivity", "androidx.profileinstaller.ProfileInstallReceiver"}
        errors = c.check_components("standard", found, ALLOWED)
        self.assertEqual(len(errors), 1)
        self.assertIn("BootReceiver", errors[0])


if __name__ == "__main__":
    unittest.main()
