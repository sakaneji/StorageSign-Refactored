"""Shared reader and query operations for the persistent StorageSign index."""

from __future__ import annotations

import csv
import io
import json
import struct
import uuid
import zlib
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


MAGIC = 0x53534958
VERSION = 2
MAX_IDENTIFIER_BYTES = 65_536
MAX_ENTRIES = 10_000_000
REPO_ROOT = Path(__file__).resolve().parents[1]
MIN_ENTRY_BYTES_V1 = 45
MIN_ENTRY_BYTES_V2 = 46
DEFAULT_INDEX_PATHS = (
    REPO_ROOT / "plugins/StorageSign-Refactored/storage-sign-index.bin",
    REPO_ROOT / "plugins/StorageSign-Refactored/storage-sign-index.bin.tmp",
)
WorldMap = dict[str, str]


@dataclass(frozen=True, slots=True)
class Entry:
    world_id: str
    x: int
    y: int
    z: int
    identifier: str
    amount: int
    verified_at: int
    front_facing: str | None = None


def default_index_path() -> Path:
    for candidate in DEFAULT_INDEX_PATHS:
        if candidate.exists():
            return candidate
    return DEFAULT_INDEX_PATHS[0]


def load_world_map(path: Path | None) -> WorldMap:
    if path is None:
        return {}
    if not path.exists():
        raise FileNotFoundError(f"world map file not found: {path}")
    raw = path.read_text(encoding="utf-8").strip()
    if not raw:
        return {}
    if path.suffix.lower() == ".csv":
        return _load_world_map_csv(path)
    data = json.loads(raw)
    if isinstance(data, dict):
        return {str(key): str(value) for key, value in data.items()}
    if isinstance(data, list):
        mapping: WorldMap = {}
        for item in data:
            if not isinstance(item, dict):
                raise ValueError("world map array entries must be objects")
            uuid_value = item.get("uuid") or item.get("world_id") or item.get("id")
            name_value = item.get("name") or item.get("world_name")
            if uuid_value is None or name_value is None:
                raise ValueError("world map entries need uuid/name fields")
            mapping[str(uuid_value)] = str(name_value)
        return mapping
    raise ValueError("world map must be a JSON object or array")


def _load_world_map_csv(path: Path) -> WorldMap:
    mapping: WorldMap = {}
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            if not row:
                continue
            uuid_value = row.get("uuid") or row.get("world_id") or row.get("id")
            name_value = row.get("name") or row.get("world_name")
            if not uuid_value or not name_value:
                raise ValueError("world map CSV needs uuid/name columns")
            mapping[uuid_value.strip()] = name_value.strip()
    return mapping


def read_index(path: Path) -> list[Entry]:
    raw = path.read_bytes()
    if len(raw) < 16:
        raise ValueError("index file is truncated")

    payload, stored_crc = raw[:-4], raw[-4:]
    if zlib.crc32(payload) & 0xFFFFFFFF != struct.unpack(">I", stored_crc)[0]:
        raise ValueError("index CRC mismatch")

    stream = io.BytesIO(payload)
    magic, version, count = struct.unpack(">III", _read_exact(stream, 12))
    if magic != MAGIC:
        raise ValueError("invalid index magic")
    if version not in (1, VERSION):
        raise ValueError(f"unsupported index version: {version}")
    if count > MAX_ENTRIES:
        raise ValueError(f"invalid index count: {count}")
    min_entry_bytes = MIN_ENTRY_BYTES_V2 if version >= 2 else MIN_ENTRY_BYTES_V1
    if count > (len(payload) - 12) // min_entry_bytes:
        raise ValueError(f"index count exceeds available data: {count}")

    entries: list[Entry] = []
    for _ in range(count):
        world_msb, world_lsb = struct.unpack(">qq", _read_exact(stream, 16))
        x, y, z, amount = struct.unpack(">iiii", _read_exact(stream, 16))
        (verified_at,) = struct.unpack(">q", _read_exact(stream, 8))
        front_facing = None
        if version >= 2:
            (has_front_facing,) = struct.unpack(">?", _read_exact(stream, 1))
            if has_front_facing:
                front_facing = _read_utf(stream)
        (identifier_length,) = struct.unpack(">i", _read_exact(stream, 4))
        if identifier_length <= 0 or identifier_length > MAX_IDENTIFIER_BYTES:
            raise ValueError(f"invalid identifier length: {identifier_length}")
        identifier = _read_exact(stream, identifier_length).decode("utf-8")
        world_id = str(uuid.UUID(bytes=struct.pack(">qq", world_msb, world_lsb)))
        entries.append(Entry(world_id, x, y, z, identifier, amount, verified_at, front_facing))

    if stream.read(1):
        raise ValueError("unexpected trailing index data")
    return entries


def _read_exact(stream: io.BytesIO, size: int) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise ValueError("index file is truncated")
    return data


def _read_utf(stream: io.BytesIO) -> str:
    (length,) = struct.unpack(">H", _read_exact(stream, 2))
    return _read_exact(stream, length).decode("utf-8")


def filter_entries(
    entries: Sequence[Entry],
    identifier: str | None = None,
    contains: bool = False,
    world: str | None = None,
) -> list[Entry]:
    needle = identifier.lower() if identifier else None
    world_filter = world.lower() if world else None
    filtered = []
    for entry in entries:
        if world_filter and entry.world_id.lower() != world_filter:
            continue
        if needle:
            haystack = entry.identifier.lower()
            identifier_matches = needle in haystack if contains else needle == haystack
            if not identifier_matches:
                continue
        filtered.append(entry)
    return filtered


def summarize(entries: Sequence[Entry], world_map: WorldMap | None = None) -> dict[str, object]:
    by_world = defaultdict(int)
    total_amount = 0
    mapped_worlds = set()
    for entry in entries:
        by_world[entry.world_id] += 1
        total_amount += entry.amount
        if world_map and entry.world_id in world_map:
            mapped_worlds.add(entry.world_id)
    return {
        "count": len(entries),
        "total_amount": total_amount,
        "worlds": dict(sorted(by_world.items())),
        "mapped_worlds": len(mapped_worlds),
    }


def entry_payload(entry: Entry, world_map: WorldMap | None = None) -> dict[str, object]:
    return {
        "world_id": entry.world_id,
        "world_name": world_map.get(entry.world_id) if world_map else None,
        "x": entry.x,
        "y": entry.y,
        "z": entry.z,
        "identifier": entry.identifier,
        "amount": entry.amount,
        "verified_at": entry.verified_at,
        "front_facing": entry.front_facing,
    }


def result_payload(
    path: Path,
    entries: Sequence[Entry],
    world_map: WorldMap | None = None,
    summary_entries: Sequence[Entry] | None = None,
) -> dict[str, object]:
    return {
        "path": str(path),
        "summary": summarize(summary_entries if summary_entries is not None else entries, world_map),
        "entries": [entry_payload(entry, world_map) for entry in entries],
    }


def text_summary(path: Path, entries: Sequence[Entry], world_map: WorldMap | None = None) -> str:
    summary = summarize(entries, world_map)
    lines = [
        f"path: {path}",
        f"entries: {summary['count']}",
        f"totalAmount: {summary['total_amount']}",
        f"worlds: {len(summary['worlds'])}",
        f"mappedWorlds: {summary['mapped_worlds']}",
    ]
    return "\n".join(lines)


def text_result(
    path: Path,
    entries: Sequence[Entry],
    world_map: WorldMap | None = None,
    summary_entries: Sequence[Entry] | None = None,
) -> str:
    lines = [text_summary(path, summary_entries if summary_entries is not None else entries, world_map)]
    for index, entry in enumerate(entries, 1):
        world_name = world_map.get(entry.world_id) if world_map else None
        world_label = f"{world_name} ({entry.world_id})" if world_name else entry.world_id
        lines.append(
            f"{index}. {world_label} {entry.x} {entry.y} {entry.z} - "
            f"{entry.amount} - {entry.identifier} [verified={entry.verified_at}]"
        )
    return "\n".join(lines)


def csv_result(entries: Sequence[Entry], world_map: WorldMap | None = None) -> str:
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(("world_id", "world_name", "x", "y", "z", "identifier", "amount", "verified_at"))
    for entry in entries:
        writer.writerow((
            entry.world_id,
            _csv_safe(world_map.get(entry.world_id) if world_map else ""),
            entry.x,
            entry.y,
            entry.z,
            _csv_safe(entry.identifier),
            entry.amount,
            entry.verified_at,
        ))
    return buffer.getvalue()


def _csv_safe(value: str | None) -> str:
    """Prevent spreadsheet applications from evaluating exported text as a formula."""
    if value is None:
        return ""
    return "'" + value if value.startswith(("=", "+", "-", "@")) else value


def json_result(
    path: Path,
    entries: Sequence[Entry],
    world_map: WorldMap | None = None,
    summary_entries: Sequence[Entry] | None = None,
) -> str:
    return json.dumps(result_payload(path, entries, world_map, summary_entries), ensure_ascii=False, indent=2)


def write_index(path: Path, entries: Sequence[Entry]) -> int:
    if len(entries) > MAX_ENTRIES:
        raise ValueError("too many index entries")
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = io.BytesIO()
    payload.write(struct.pack(">III", MAGIC, VERSION, len(entries)))
    for entry in entries:
        world_uuid = uuid.UUID(entry.world_id)
        world_msb, world_lsb = struct.unpack(">qq", world_uuid.bytes)
        identifier = entry.identifier.encode("utf-8")
        if not identifier:
            raise ValueError("identifier is required")
        if len(identifier) > MAX_IDENTIFIER_BYTES:
            raise ValueError("identifier is too long")
        payload.write(struct.pack(">qqiiiiq", world_msb, world_lsb, entry.x, entry.y, entry.z,
                                  entry.amount, entry.verified_at))
        payload.write(struct.pack(">?", entry.front_facing is not None))
        if entry.front_facing is not None:
            facing = entry.front_facing.encode("utf-8")
            payload.write(struct.pack(">H", len(facing)))
            payload.write(facing)
        payload.write(struct.pack(">i", len(identifier)))
        payload.write(identifier)
    raw = payload.getvalue()
    crc = zlib.crc32(raw) & 0xFFFFFFFF
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(raw + struct.pack(">I", crc))
    temporary.replace(path)
    return path.stat().st_size
