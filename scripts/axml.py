"""Lettura del manifest binario (AXML) di un APK, solo libreria standard.

Restituisce i componenti nella stessa forma di `rea inspect-android-package`
(normalized_result.components.items), così audit_app_musicali.py li tratta allo
stesso modo. Serve quando JADX/REA non sono disponibili: basta il file APK.
Accetta anche i pacchetti divisi (.apks, .xapk, .apkm): usa il base.apk interno.
"""

from __future__ import annotations

import io
import struct
import zipfile
from pathlib import Path

ATTR_NAME = 0x01010003
ATTR_PERMISSION = 0x01010006
ATTR_EXPORTED = 0x01010010
COMPONENT_TAGS = ("activity", "activity-alias", "service", "receiver", "provider")

_RES_STRING_POOL = 0x0001
_RES_XML_START_ELEMENT = 0x0102
_RES_XML_END_ELEMENT = 0x0103
_RES_XML_RESOURCE_MAP = 0x0180
_TYPE_STRING = 0x03
_TYPE_BOOLEAN = 0x12


def _read_string_pool(data: bytes, pos: int, header_size: int) -> list[str]:
    count, _style_count, flags, strings_start, _styles_start = struct.unpack_from("<IIIII", data, pos + 8)
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from(f"<{count}I", data, pos + header_size)
    base = pos + strings_start
    out = []
    for off in offsets:
        p = base + off
        if utf8:
            n = data[p]; p += 1          # lunghezza in caratteri (ignorata)
            if n & 0x80:
                p += 1
            n = data[p]; p += 1          # lunghezza in byte
            if n & 0x80:
                n = ((n & 0x7F) << 8) | data[p]; p += 1
            out.append(data[p:p + n].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]; p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]; p += 2
            out.append(data[p:p + 2 * n].decode("utf-16-le", "replace"))
    return out


def parse_manifest(data: bytes) -> dict:
    """{'package', 'components': {'status', 'items': [...]}} da un AndroidManifest.xml binario."""
    strings: list[str] = []
    res_ids: list[int] = []
    package = None
    items: list[dict] = []
    current: dict | None = None      # componente aperto
    depth_in_component = 0
    pos = 8                          # salta l'intestazione RES_XML_TYPE
    while pos + 8 <= len(data):
        chunk_type, header_size, size = struct.unpack_from("<HHI", data, pos)
        if size < 8:
            break
        if chunk_type == _RES_STRING_POOL:
            strings = _read_string_pool(data, pos, header_size)
        elif chunk_type == _RES_XML_RESOURCE_MAP:
            res_ids = list(struct.unpack_from(f"<{(size - header_size) // 4}I", data, pos + header_size))
        elif chunk_type == _RES_XML_START_ELEMENT:
            _ns, name_idx, attr_start, attr_size, attr_count = struct.unpack_from("<IIHHH", data, pos + 16)
            tag = strings[name_idx]
            attrs: dict = {}
            for i in range(attr_count):
                a = pos + 16 + attr_start + i * attr_size
                _ans, aname, raw, _vsize, _res0, vtype, vdata = struct.unpack_from("<IIIHBBI", data, a)
                key = res_ids[aname] if aname < len(res_ids) else strings[aname]
                if raw != 0xFFFFFFFF:
                    value = strings[raw]
                elif vtype == _TYPE_STRING:
                    value = strings[vdata]
                elif vtype == _TYPE_BOOLEAN:
                    value = vdata != 0
                else:
                    value = vdata
                attrs[key] = value
                if isinstance(key, str):
                    attrs.setdefault(key, value)
            if tag == "manifest":
                package = attrs.get("package")
            if current is not None:
                depth_in_component += 1
                if tag == "intent-filter":
                    current["intent_filter_count"] += 1
                elif tag == "action" and isinstance(attrs.get(ATTR_NAME), str):
                    current["actions"].append(attrs[ATTR_NAME])
                elif tag == "category" and isinstance(attrs.get(ATTR_NAME), str):
                    current["categories"].append(attrs[ATTR_NAME])
            elif tag in COMPONENT_TAGS:
                exported = attrs.get(ATTR_EXPORTED)
                current = {
                    "kind": tag,
                    "declared_name": attrs.get(ATTR_NAME),
                    "name": attrs.get(ATTR_NAME),
                    "exported": exported if isinstance(exported, bool) else None,
                    "exported_default": None,
                    "permission": attrs.get(ATTR_PERMISSION) if isinstance(attrs.get(ATTR_PERMISSION), str) else None,
                    "intent_filter_count": 0,
                    "actions": [],
                    "categories": [],
                }
                depth_in_component = 0
        elif chunk_type == _RES_XML_END_ELEMENT and current is not None:
            if depth_in_component == 0:
                if current["exported"] is None:
                    current["exported_default"] = current["intent_filter_count"] > 0
                items.append(current)
                current = None
            else:
                depth_in_component -= 1
        pos += size
    for item in items:
        name = item["name"] or ""
        if package and name.startswith("."):
            item["name"] = package + name
        elif package and name and "." not in name:
            item["name"] = f"{package}.{name}"
    return {"package": package, "components": {"status": "complete", "items": items}}


def manifest_bytes(path: Path) -> bytes:
    """Il manifest binario di un APK o del base.apk dentro un pacchetto diviso."""
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if "AndroidManifest.xml" in names:
            return z.read("AndroidManifest.xml")
        inner = [n for n in names if n.lower().endswith(".apk")]
        inner.sort(key=lambda n: (Path(n).name != "base.apk", -z.getinfo(n).file_size))
        for name in inner:
            with zipfile.ZipFile(io.BytesIO(z.read(name))) as apk:
                if "AndroidManifest.xml" in apk.namelist():
                    return apk.read("AndroidManifest.xml")
    raise ValueError(f"{path.name}: nessun AndroidManifest.xml (APK cifrato o non valido?)")


def inspect_apk(path: Path) -> dict:
    """Come `rea inspect-android-package --json`, ridotto a ciò che serve."""
    return {"normalized_result": parse_manifest(manifest_bytes(path))}
