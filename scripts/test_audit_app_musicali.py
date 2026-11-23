"""Test di scripts/audit_app_musicali.py: python3 -m unittest discover -s scripts"""

import unittest

import audit_app_musicali as a

# Forma di normalized_result.components in `rea inspect-android-package --json`
# (src/android/AndroidManifestComponents.ts di rea-agents 6.10.1).
EVIDENCE = {
    "normalized_result": {
        "components": {
            "status": "complete",
            "items": [
                {"kind": "service", "name": "com.example.MusicService", "exported": True,
                 "exported_default": True, "permission": None,
                 "actions": ["android.media.browse.MediaBrowserService"]},
                {"kind": "service", "name": "com.example.PlaybackService", "exported": None,
                 "exported_default": False, "permission": None,
                 "actions": ["androidx.media3.session.MediaSessionService"]},
                {"kind": "receiver", "name": "androidx.media.session.MediaButtonReceiver",
                 "exported": True, "permission": None,
                 "actions": ["android.intent.action.MEDIA_BUTTON"]},
                {"kind": "activity", "name": "com.example.Main", "exported": True,
                 "actions": ["android.intent.action.MAIN"]},
            ],
        }
    }
}

DUMPSYS = """MEDIA SESSION SERVICE (dumpsys media_session)
  Sessions Stack - have 2 sessions:
    spotify-media-session com.spotify.music/spotify-media-session (userId=0)
      ownerPid=1234, ownerUid=10123, userId=0
      package=com.spotify.music
      launchIntent=PendingIntent{1 (null)}
      mediaButtonReceiver=MBR {pi=PendingIntent{2}, type=1}
      active=true
      flags=3
      state=PlaybackState {state=3, position=1000, buffered position=0, speed=1.0, updated=5, actions=2871, custom actions=[], active item id=-1, error=null}
    MediaSessionCompat com.google.android.apps.youtube.music/MediaSessionCompat (userId=0)
      package=com.google.android.apps.youtube.music
      active=false
      state=PlaybackState {state=2, position=0, buffered position=0, speed=0.0, updated=5, actions=518, custom actions=[], active item id=-1, error=null}
"""


class CatalogoTest(unittest.TestCase):
    def test_legge_le_13_app_di_music_app_kt(self):
        catalog = a.parse_catalog(a.MUSIC_APP_KT.read_text(encoding="utf-8"))
        self.assertEqual(len(catalog), 13)
        self.assertEqual(catalog[0], ("Spotify", ["com.spotify.music"]))
        self.assertIn(("Brave Browser", ["com.brave.browser"]), catalog)


class ManifestTest(unittest.TestCase):
    def test_riassume_le_dichiarazioni_utili(self):
        s = a.summarize_components(EVIDENCE)
        self.assertEqual(s["media_browser_service"][0]["nome"], "com.example.MusicService")
        self.assertTrue(s["media_browser_service"][0]["esportato"])
        self.assertFalse(s["media3_session_service"][0]["esportato"])
        self.assertEqual(len(s["ricevitori_media_button"]), 1)
        self.assertEqual(s["attivita_play_from_search"], [])
        self.assertEqual(s["servizi_esportati"], ["com.example.MusicService"])
        self.assertEqual(s["copertura_manifest"], "complete")

    def test_risultato_vuoto_non_rompe(self):
        s = a.summarize_components({})
        self.assertEqual(s["media_browser_service"], [])


class SessioniTest(unittest.TestCase):
    def test_sessione_spotify(self):
        sessions = a.parse_media_sessions(DUMPSYS, "com.spotify.music")
        self.assertEqual(len(sessions), 1)
        s = sessions[0]
        self.assertTrue(s["attiva"])
        self.assertEqual(s["stato"], "PLAYING")
        self.assertIn("PLAY", s["azioni"])
        self.assertIn("PLAY_FROM_SEARCH", s["azioni"])
        self.assertTrue(s["media_button_receiver"].startswith("MBR"))

    def test_filtra_per_pacchetto(self):
        sessions = a.parse_media_sessions(DUMPSYS, "com.google.android.apps.youtube.music")
        self.assertEqual(len(sessions), 1)
        self.assertEqual(sessions[0]["stato"], "PAUSED")
        self.assertEqual(sessions[0]["azioni"], ["PAUSE", "PLAY", "PLAY_PAUSE"])
        self.assertEqual(a.parse_media_sessions(DUMPSYS, "com.aspiro.tidal"), [])

    def test_azioni(self):
        self.assertEqual(a.decode_actions(4 | 512), ["PLAY", "PLAY_PAUSE"])


class MarkdownTest(unittest.TestCase):
    def test_righe(self):
        rows = a.markdown_rows([
            {"app": "Spotify", "pacchetto": "com.spotify.music", "installata": True,
             "manifest": a.summarize_components(EVIDENCE),
             "sessioni": a.parse_media_sessions(DUMPSYS, "com.spotify.music")},
            {"app": "Tidal", "pacchetto": "com.aspiro.tidal", "installata": False},
        ])
        self.assertIn("| Spotify | com.spotify.music | sì | sì (non esportato) | sì | no | PLAYING:", rows)
        self.assertIn("| Tidal | com.aspiro.tidal | non installata |", rows)


if __name__ == "__main__":
    unittest.main()
