"""Offline reader for Minecraft region files used to rebuild StorageSign index data."""

from __future__ import annotations

import gzip
import io
import json
import struct
import time
import uuid
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Sequence

from storage_sign_index import Entry, write_index


SECTOR_BYTES = 4096
HEADER_BYTES = SECTOR_BYTES * 2
TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12

ROTATION_FACES = (
    "SOUTH",
    "SOUTH_SOUTH_WEST",
    "SOUTH_WEST",
    "WEST_SOUTH_WEST",
    "WEST",
    "WEST_NORTH_WEST",
    "NORTH_WEST",
    "NORTH_NORTH_WEST",
    "NORTH",
    "NORTH_NORTH_EAST",
    "NORTH_EAST",
    "EAST_NORTH_EAST",
    "EAST",
    "EAST_SOUTH_EAST",
    "SOUTH_EAST",
    "SOUTH_SOUTH_EAST",
)

PERSISTENT_DATA_CONTAINERS = ("PublicBukkitValues", "BukkitValues")
STORAGE_IDENTIFIER_KEYS = (
    "storagesign:storage_identifier",
    "storagesign:potion_identifier",
)

CARDINAL_FACES = {
    "north": "NORTH",
    "south": "SOUTH",
    "west": "WEST",
    "east": "EAST",
    "up": "UP",
    "down": "DOWN",
}


@dataclass(frozen=True, slots=True)
class OfflineWorld:
    path: Path
    world_id: str


def discover_worlds(world_dirs: Sequence[Path], warn: Callable[[str], None] | None = None) -> list[OfflineWorld]:
    inputs: list[OfflineWorld] = []
    for world_dir in world_dirs:
        resolved = world_dir.resolve()
        if not resolved.is_dir():
            _warn(warn, f"warning: skipping world {world_dir}: world directory not found")
            continue
        try:
            world_id = str(read_world_uuid(resolved))
        except Exception as exc:
            _warn(warn, f"warning: skipping world {world_dir}: {exc}")
            continue
        inputs.append(OfflineWorld(resolved, world_id))
    return inputs


def rebuild_index(world_dirs: Sequence[Path], warn: Callable[[str], None] | None = None) -> list[Entry]:
    entries: list[Entry] = []
    worlds = discover_worlds(world_dirs, warn=warn)
    if not worlds:
        raise ValueError("no valid world directories found")
    for world in worlds:
        entries.extend(scan_world(world, warn=warn))
    entries.sort(key=lambda entry: (entry.world_id, entry.x, entry.y, entry.z, entry.identifier))
    return entries


def write_rebuilt_index(output: Path, world_dirs: Sequence[Path],
                        warn: Callable[[str], None] | None = None) -> int:
    entries = rebuild_index(world_dirs, warn=warn)
    write_index(output, entries)
    return len(entries)


def scan_world(world: OfflineWorld, warn: Callable[[str], None] | None = None) -> list[Entry]:
    region_dir = world.path / "region"
    if not region_dir.is_dir():
        raise FileNotFoundError(f"region directory not found: {region_dir}")
    entries: list[Entry] = []
    for region_path in sorted(region_dir.glob("r.*.*.mca")):
        try:
            entries.extend(scan_region(region_path, world.world_id, warn=warn))
        except Exception as exc:
            _warn(warn, f"warning: skipping region {region_path}: {exc}")
    return entries


def scan_region(path: Path, world_id: str, warn: Callable[[str], None] | None = None) -> list[Entry]:
    raw = path.read_bytes()
    if len(raw) < HEADER_BYTES:
        raise ValueError(f"region file is truncated: {path}")
    chunk_offsets = [struct.unpack(">I", raw[i : i + 4])[0] for i in range(0, SECTOR_BYTES, 4)]
    entries: list[Entry] = []
    for index, value in enumerate(chunk_offsets):
        sector_offset = value >> 8
        sector_count = value & 0xFF
        if sector_offset == 0 or sector_count == 0:
            continue
        chunk_x = index % 32
        chunk_z = index // 32
        try:
            start = sector_offset * SECTOR_BYTES
            end = start + sector_count * SECTOR_BYTES
            if end > len(raw):
                raise ValueError(f"chunk extends past end of region file: {path}")
            chunk = _decode_chunk(raw[start:end], path)
            entries.extend(scan_chunk(chunk, world_id))
        except Exception as exc:
            _warn(warn, f"warning: skipping chunk {path} [{chunk_x},{chunk_z}]: {exc}")
    return entries


def scan_chunk(chunk: dict[str, Any], world_id: str) -> list[Entry]:
    root = chunk.get("Level") if "Level" in chunk else chunk
    if not isinstance(root, dict):
        return []
    sections = root.get("sections") or root.get("Sections") or []
    section_map: dict[int, dict[str, Any]] = {}
    for section in sections:
        if not isinstance(section, dict):
            continue
        section_y = section.get("Y")
        if section_y is None:
            section_y = section.get("y")
        if section_y is None:
            continue
        try:
            section_map[int(section_y)] = section
        except (TypeError, ValueError):
            continue
    block_entities = (
        root.get("block_entities")
        or root.get("blockEntities")
        or root.get("TileEntities")
        or []
    )
    entries: list[Entry] = []
    for block_entity in block_entities:
        if not isinstance(block_entity, dict):
            continue
        identifier = _string_value(block_entity.get("id"))
        if not _is_sign_block_entity(identifier):
            continue
        x = _int_value(block_entity.get("x"))
        y = _int_value(block_entity.get("y"))
        z = _int_value(block_entity.get("z"))
        if x is None or y is None or z is None:
            continue
        lines = _extract_sign_lines(block_entity)
        if lines is None:
            continue
        parsed = parse_storage_sign_lines(lines, _persistent_storage_identifier(block_entity))
        if parsed is None:
            continue
        sign_state = _block_state_at(section_map, x, y, z)
        front_facing = _front_facing(sign_state)
        entries.append(Entry(world_id, x, y, z, parsed[0], parsed[1], _scan_time_epoch_millis(), front_facing))
    return entries


def parse_storage_sign_lines(
    lines: Sequence[str], canonical_identifier: str | None = None
) -> tuple[str, int] | None:
    if len(lines) < 3:
        return None
    if lines[0] != "StorageSign":
        return None
    identifier = canonical_identifier.strip() if canonical_identifier else lines[1].strip()
    if not identifier or identifier == "Empty":
        return None
    amount = _parse_int(lines[2])
    if amount is None or amount < 0:
        return None
    return identifier, amount


def _persistent_storage_identifier(block_entity: dict[str, Any]) -> str | None:
    for container_name in PERSISTENT_DATA_CONTAINERS:
        container = block_entity.get(container_name)
        if not isinstance(container, dict):
            continue
        for identifier_key in STORAGE_IDENTIFIER_KEYS:
            identifier = _string_value(container.get(identifier_key))
            if identifier is not None and identifier.strip():
                return identifier.strip()
    return None


def read_world_uuid(world_dir: Path) -> uuid.UUID:
    uid_path = world_dir / "uid.dat"
    if uid_path.exists():
        raw = uid_path.read_bytes()
        if len(raw) == 16:
            return uuid.UUID(bytes=raw)
    level_dat = world_dir / "level.dat"
    if not level_dat.exists():
        raise FileNotFoundError(f"uid.dat and level.dat not found: {world_dir}")
    root = _read_nbt_file(level_dat)
    data = root.get("Data") if isinstance(root, dict) else None
    if not isinstance(data, dict):
        data = root if isinstance(root, dict) else None
    if not isinstance(data, dict):
        raise ValueError(f"level.dat does not contain a Data compound: {level_dat}")
    world_uuid = data.get("WorldUUID")
    if isinstance(world_uuid, str) and world_uuid.strip():
        return uuid.UUID(world_uuid.strip())
    most = _int_value(data.get("WorldUUIDMost"))
    least = _int_value(data.get("WorldUUIDLeast"))
    if most is None or least is None:
        raise ValueError(f"world UUID not found in level.dat: {level_dat}")
    return uuid.UUID(int=((most & ((1 << 64) - 1)) << 64) | (least & ((1 << 64) - 1)))


def _scan_time_epoch_millis() -> int:
    return int(time.time() * 1000)


def _extract_sign_lines(block_entity: dict[str, Any]) -> list[str] | None:
    front_text = block_entity.get("front_text") or block_entity.get("frontText")
    if isinstance(front_text, dict):
        messages = front_text.get("messages") or front_text.get("Messages")
        lines = _messages_to_lines(messages)
        if lines is not None:
            return lines
    legacy = []
    for key in ("Text1", "Text2", "Text3", "Text4"):
        legacy.append(_component_text(block_entity.get(key)))
    if any(line for line in legacy):
        return legacy
    return None


def _messages_to_lines(messages: Any) -> list[str] | None:
    if not isinstance(messages, list) or len(messages) < 4:
        return None
    return [_component_text(messages[i]) for i in range(4)]


def _component_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        stripped = value.strip()
        if not stripped:
            return ""
        if stripped[0] in "{[":
            try:
                return _component_text(json.loads(stripped))
            except json.JSONDecodeError:
                return value
        return value
    if isinstance(value, dict):
        text = _component_text(value.get("text"))
        if not text and "translate" in value:
            text = str(value.get("translate") or "")
        extras = value.get("extra")
        if isinstance(extras, list):
            text += "".join(_component_text(item) for item in extras)
        if isinstance(value.get("with"), list):
            text += "".join(_component_text(item) for item in value["with"])
        return text
    if isinstance(value, list):
        return "".join(_component_text(item) for item in value)
    return str(value)


def _block_state_at(section_map: dict[int, dict[str, Any]], x: int, y: int, z: int) -> dict[str, Any] | None:
    section_y = y >> 4
    section = section_map.get(section_y)
    if section is None:
        return None
    block_states = section.get("block_states") or section.get("blockStates")
    if not isinstance(block_states, dict):
        return None
    palette = block_states.get("palette")
    if not isinstance(palette, list) or not palette:
        return None
    data = block_states.get("data")
    palette_index = _palette_index(data, len(palette), x & 15, y & 15, z & 15)
    if palette_index is None or palette_index >= len(palette):
        return None
    state = palette[palette_index]
    return state if isinstance(state, dict) else None


def _palette_index(data: Any, palette_size: int, local_x: int, local_y: int, local_z: int) -> int | None:
    if not isinstance(data, list) or not data:
        return 0 if palette_size == 1 else None
    bits = max(4, (palette_size - 1).bit_length())
    values_per_long = 64 // bits
    index = (local_y << 8) | (local_z << 4) | local_x
    long_index = index // values_per_long
    bit_index = (index % values_per_long) * bits
    if long_index >= len(data):
        return None
    current = int(data[long_index]) & ((1 << 64) - 1)
    value = (current >> bit_index) & ((1 << bits) - 1)
    spill = bit_index + bits - 64
    if spill > 0 and long_index + 1 < len(data):
        next_value = int(data[long_index + 1]) & ((1 << 64) - 1)
        value |= (next_value & ((1 << spill) - 1)) << (bits - spill)
    return value


def _front_facing(state: dict[str, Any] | None) -> str | None:
    if not state:
        return None
    properties = state.get("Properties")
    if not isinstance(properties, dict):
        return None
    facing = properties.get("facing")
    if isinstance(facing, str) and facing.lower() in CARDINAL_FACES:
        return CARDINAL_FACES[facing.lower()]
    rotation = properties.get("rotation")
    if rotation is None:
        return None
    parsed = _parse_int(rotation)
    if parsed is None:
        return None
    return ROTATION_FACES[parsed % len(ROTATION_FACES)]


def _is_sign_block_entity(identifier: str | None) -> bool:
    if not identifier:
        return False
    lowered = identifier.lower()
    return lowered.endswith("_sign") or lowered.endswith("_hanging_sign") or lowered in {
        "minecraft:sign",
        "minecraft:hanging_sign",
    }


def _decode_chunk(raw: bytes, path: Path) -> dict[str, Any]:
    if len(raw) < 5:
        raise ValueError(f"chunk is truncated: {path}")
    (length,) = struct.unpack(">I", raw[:4])
    if length <= 0 or length > len(raw) - 4:
        raise ValueError(f"invalid chunk length in {path}")
    compression = raw[4]
    payload = raw[5 : 4 + length]
    if compression == 1:
        data = gzip.decompress(payload)
    elif compression == 2:
        data = zlib.decompress(payload)
    elif compression == 3:
        data = payload
    else:
        raise ValueError(f"unsupported chunk compression {compression} in {path}")
    nbt = _read_nbt_bytes(data)
    if not isinstance(nbt, dict):
        raise ValueError(f"chunk root is not a compound in {path}")
    return nbt


def _read_nbt_file(path: Path) -> Any:
    raw = path.read_bytes()
    try:
        raw = gzip.decompress(raw)
    except OSError:
        pass
    return _read_nbt_bytes(raw)


def _read_nbt_bytes(data: bytes) -> Any:
    stream = io.BytesIO(data)
    tag_type = _read_byte(stream)
    if tag_type != TAG_COMPOUND:
        raise ValueError("NBT root must be a compound")
    _read_string(stream)
    return _read_tag_payload(stream, tag_type)


def _read_tag_payload(stream: io.BytesIO, tag_type: int) -> Any:
    if tag_type == TAG_END:
        return None
    if tag_type == TAG_BYTE:
        return _read_signed(stream, ">b", 1)
    if tag_type == TAG_SHORT:
        return _read_signed(stream, ">h", 2)
    if tag_type == TAG_INT:
        return _read_signed(stream, ">i", 4)
    if tag_type == TAG_LONG:
        return _read_signed(stream, ">q", 8)
    if tag_type == TAG_FLOAT:
        return _read_signed(stream, ">f", 4)
    if tag_type == TAG_DOUBLE:
        return _read_signed(stream, ">d", 8)
    if tag_type == TAG_BYTE_ARRAY:
        return _read_byte_array(stream)
    if tag_type == TAG_STRING:
        return _read_string(stream)
    if tag_type == TAG_LIST:
        return _read_list(stream)
    if tag_type == TAG_COMPOUND:
        return _read_compound(stream)
    if tag_type == TAG_INT_ARRAY:
        return _read_int_array(stream)
    if tag_type == TAG_LONG_ARRAY:
        return _read_long_array(stream)
    raise ValueError(f"unsupported NBT tag: {tag_type}")


def _read_compound(stream: io.BytesIO) -> dict[str, Any]:
    value: dict[str, Any] = {}
    while True:
        tag_type = _read_byte(stream)
        if tag_type == TAG_END:
            return value
        name = _read_string(stream)
        value[name] = _read_tag_payload(stream, tag_type)


def _read_list(stream: io.BytesIO) -> list[Any]:
    tag_type = _read_byte(stream)
    length = _read_int(stream)
    if length < 0:
        raise ValueError("NBT list length is negative")
    return [_read_tag_payload(stream, tag_type) for _ in range(length)]


def _read_byte_array(stream: io.BytesIO) -> bytes:
    length = _read_int(stream)
    if length < 0:
        raise ValueError("NBT byte array length is negative")
    return _read_exact(stream, length)


def _read_int_array(stream: io.BytesIO) -> list[int]:
    length = _read_int(stream)
    if length < 0:
        raise ValueError("NBT int array length is negative")
    return [_read_int(stream) for _ in range(length)]


def _read_long_array(stream: io.BytesIO) -> list[int]:
    length = _read_int(stream)
    if length < 0:
        raise ValueError("NBT long array length is negative")
    return [_read_long(stream) for _ in range(length)]


def _read_string(stream: io.BytesIO) -> str:
    length = _read_unsigned_short(stream)
    return _read_exact(stream, length).decode("utf-8")


def _read_byte(stream: io.BytesIO) -> int:
    return struct.unpack(">B", _read_exact(stream, 1))[0]


def _read_unsigned_short(stream: io.BytesIO) -> int:
    return struct.unpack(">H", _read_exact(stream, 2))[0]


def _read_int(stream: io.BytesIO) -> int:
    return struct.unpack(">i", _read_exact(stream, 4))[0]


def _read_long(stream: io.BytesIO) -> int:
    return struct.unpack(">q", _read_exact(stream, 8))[0]


def _read_signed(stream: io.BytesIO, fmt: str, size: int) -> Any:
    return struct.unpack(fmt, _read_exact(stream, size))[0]


def _read_exact(stream: io.BytesIO, size: int) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise ValueError("NBT data is truncated")
    return data


def _string_value(value: Any) -> str | None:
    return value if isinstance(value, str) else None


def _int_value(value: Any) -> int | None:
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        try:
            return int(value)
        except ValueError:
            return None
    return None


def _parse_int(value: Any) -> int | None:
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        try:
            return int(value.strip())
        except ValueError:
            return None
    return None


def _warn(warn: Callable[[str], None] | None, message: str) -> None:
    if warn is not None:
        warn(message)
