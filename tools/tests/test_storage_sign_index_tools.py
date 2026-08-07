from __future__ import annotations

import contextlib
import base64
import gzip
import io
import json
import struct
import sys
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from unittest import mock
import uuid
import zlib
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_DIR))

import storage_sign_index as index  # noqa: E402
import storage_sign_index_cli as cli  # noqa: E402
import storage_sign_index_viewer as viewer  # noqa: E402
import storage_sign_region as region  # noqa: E402
import storage_sign_region_cli as region_cli  # noqa: E402


WORLD_ID = uuid.UUID("12345678-1234-5678-9abc-def012345678")
OTHER_WORLD_ID = uuid.UUID("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
SECTOR_BYTES = 4096


def write_index(path: Path) -> None:
    entries = (
        index.Entry(str(WORLD_ID), 10, 64, -3, "STONE", 128, 1_700_000_000_000, "NORTH"),
        index.Entry(str(WORLD_ID), 11, 65, -4, "POTION:HEAL:0", 4, 1_700_000_001_000),
        index.Entry(str(OTHER_WORLD_ID), 20, 70, 30, "STONE", 32, 1_700_000_002_000, "SOUTH"),
    )
    index.write_index(path, entries)


def write_payload(path: Path, payload: bytes) -> None:
    path.write_bytes(payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF))


class StorageSignIndexTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.index_path = Path(self.temp_dir.name) / "storage-sign-index.bin"
        write_index(self.index_path)

    def test_reads_and_filters_index(self) -> None:
        entries = index.read_index(self.index_path)

        self.assertEqual(3, len(entries))
        self.assertEqual(2, len(index.filter_entries(entries, "stone")))
        self.assertEqual(1, len(index.filter_entries(entries, "heal", contains=True)))
        self.assertEqual(2, len(index.filter_entries(entries, world=str(WORLD_ID))))

    def test_reads_shared_java_python_protocol_fixture(self) -> None:
        fixture = TOOLS_DIR / "tests" / "fixtures" / "storage-sign-index-v1.base64"
        self.index_path.write_bytes(base64.b64decode(fixture.read_text(encoding="ascii")))

        entries = index.read_index(self.index_path)

        self.assertEqual([index.Entry(str(WORLD_ID), -10, 64, 30, "STONE", 128,
                                      1_700_000_000_000)], entries)

    def test_writer_matches_fixed_v2_binary_golden_and_round_trips(self) -> None:
        """Keep the production writer honest against an independently fixed wire oracle."""
        entry = index.Entry(
            str(WORLD_ID), -2_147_483_648, -64, 2_147_483_647,
            "POTION:HEAL:0", 2_147_483_647, -1, "NORTH_EAST",
        )
        output = Path(self.temp_dir.name) / "golden.bin"
        # This is a literal Java DataOutputStream-compatible v2 record plus its
        # CRC32; it deliberately does not use the production serializer/constants.
        golden = bytes.fromhex(
            "53534958000000020000000112345678123456789abcdef012345678"
            "80000000ffffffc07fffffff7fffffffffffffffffffffff01000a4e4f5254485f45415354"
            "0000000d504f54494f4e3a4845414c3a30ba68cc10"
        )

        index.write_index(output, (entry,))

        self.assertEqual(golden, output.read_bytes())
        self.assertEqual(b"SSIX", golden[:4])
        self.assertEqual(2, struct.unpack(">I", golden[4:8])[0])
        self.assertEqual(1, struct.unpack(">I", golden[8:12])[0])
        self.assertEqual(
            (0x1234567812345678, -0x6543210FEDCBA988),
            struct.unpack(">qq", golden[12:28]),
        )
        self.assertEqual(
            (-2_147_483_648, -64, 2_147_483_647, 2_147_483_647),
            struct.unpack(">iiii", golden[28:44]),
        )
        self.assertEqual(-1, struct.unpack(">q", golden[44:52])[0])
        self.assertEqual(0xBA68CC10, struct.unpack(">I", golden[-4:])[0])
        self.assertEqual((entry,), tuple(index.read_index(output)))

    def test_writer_failure_preserves_existing_index_and_removes_staging_file(self) -> None:
        original = self.index_path.read_bytes()
        temporary = self.index_path.with_name(self.index_path.name + ".tmp")
        replacement = (index.Entry(str(WORLD_ID), 1, 2, 3, "DIRT", 64, 5),)

        temporary.write_bytes(b"partial write from an interrupted attempt")
        with mock.patch.object(Path, "write_bytes", side_effect=OSError("write failed")):
            with self.assertRaisesRegex(OSError, "write failed"):
                index.write_index(self.index_path, replacement)
        self.assertEqual(original, self.index_path.read_bytes())
        self.assertFalse(temporary.exists())

        with mock.patch.object(Path, "replace", side_effect=OSError("replace failed")):
            with self.assertRaisesRegex(OSError, "replace failed"):
                index.write_index(self.index_path, replacement)
        self.assertEqual(original, self.index_path.read_bytes())
        self.assertFalse(temporary.exists())

    def test_default_index_path_uses_tmp_only_when_bin_is_absent(self) -> None:
        temporary = self.index_path.with_name(self.index_path.name + ".tmp")
        temporary.write_bytes(self.index_path.read_bytes())
        with mock.patch.object(index, "DEFAULT_INDEX_PATHS", (self.index_path, temporary)):
            self.assertEqual(self.index_path, index.default_index_path())
            self.index_path.unlink()
            self.assertEqual(temporary, index.default_index_path())

    def test_rejects_crc_mismatch(self) -> None:
        raw = bytearray(self.index_path.read_bytes())
        raw[12] ^= 0x01
        self.index_path.write_bytes(raw)

        with self.assertRaisesRegex(ValueError, "CRC mismatch"):
            index.read_index(self.index_path)

    def test_rejects_invalid_header_count_utf8_and_trailing_data(self) -> None:
        for payload, message in (
            (struct.pack(">III", 0, index.VERSION, 0), "magic"),
            (struct.pack(">III", index.MAGIC, index.VERSION + 1, 0), "version"),
            (struct.pack(">III", index.MAGIC, index.VERSION, index.MAX_ENTRIES + 1), "count"),
            (struct.pack(">III", index.MAGIC, index.VERSION, 1), "available data"),
            (struct.pack(">III", index.MAGIC, index.VERSION, 0) + b"x", "trailing"),
        ):
            with self.subTest(message=message):
                write_payload(self.index_path, payload)
                with self.assertRaisesRegex(ValueError, message):
                    index.read_index(self.index_path)

        world_msb, world_lsb = struct.unpack(">qq", WORLD_ID.bytes)
        payload = struct.pack(">IIIqqiiiiq?", index.MAGIC, index.VERSION, 1,
                              world_msb, world_lsb, 0, 0, 0, 1, 0, False)
        payload += struct.pack(">i", 1) + b"\xff"
        write_payload(self.index_path, payload)
        with self.assertRaises(UnicodeDecodeError):
            index.read_index(self.index_path)

        payload = struct.pack(">IIIqqiiiiq?", index.MAGIC, index.VERSION, 1,
                              world_msb, world_lsb, 0, 0, 0, -1, 0, False)
        payload += struct.pack(">i", 5) + b"STONE"
        write_payload(self.index_path, payload)
        with self.assertRaisesRegex(ValueError, "amount"):
            index.read_index(self.index_path)

        payload = struct.pack(">IIIqqiiiiq?", index.MAGIC, index.VERSION, 1,
                              world_msb, world_lsb, 0, 0, 0, 1, 0, False)
        payload += struct.pack(">i", 3) + b"   "
        write_payload(self.index_path, payload)
        with self.assertRaisesRegex(ValueError, "identifier"):
            index.read_index(self.index_path)

        payload = struct.pack(">IIIqqiiiiq?", index.MAGIC, index.VERSION, 1,
                              world_msb, world_lsb, 0, 0, 0, 1, 0, True)
        payload += struct.pack(">H", 8) + b"SIDEWAYS"
        payload += struct.pack(">i", 5) + b"STONE"
        write_payload(self.index_path, payload)
        with self.assertRaisesRegex(ValueError, "front facing"):
            index.read_index(self.index_path)

        with self.assertRaisesRegex(ValueError, "amount"):
            index.write_index(self.index_path, [index.Entry(
                str(WORLD_ID), 0, 0, 0, "STONE", -1, 0,
            )])
        with self.assertRaisesRegex(ValueError, "identifier"):
            index.write_index(self.index_path, [index.Entry(
                str(WORLD_ID), 0, 0, 0, "   ", 1, 0,
            )])
        with self.assertRaisesRegex(ValueError, "front facing"):
            index.write_index(self.index_path, [index.Entry(
                str(WORLD_ID), 0, 0, 0, "STONE", 1, 0, "SIDEWAYS",
            )])

    def test_world_map_and_serializers(self) -> None:
        world_map_path = Path(self.temp_dir.name) / "worlds.json"
        world_map_path.write_text(json.dumps({str(WORLD_ID): "world"}), encoding="utf-8")
        world_map = index.load_world_map(world_map_path)
        entries = index.filter_entries(index.read_index(self.index_path), "STONE")

        text = index.text_result(self.index_path, entries, world_map)
        csv_output = index.csv_result(entries, world_map)
        payload = json.loads(index.json_result(self.index_path, entries, world_map))

        self.assertIn(f"world ({WORLD_ID})", text)
        self.assertIn(f'{WORLD_ID},world,10,64,-3,STONE,128', csv_output)
        self.assertEqual(2, payload["summary"]["count"])
        self.assertEqual("world", payload["entries"][0]["world_name"])

    def test_csv_escapes_spreadsheet_formulas(self) -> None:
        dangerous = index.Entry(str(WORLD_ID), 0, 0, 0, "=1+1", 1, 0)
        output = index.csv_result((dangerous,), {str(WORLD_ID): "@world"})
        self.assertIn("'@world", output)
        self.assertIn("'=1+1", output)

    def test_cli_search_limit_preserves_total_match_count(self) -> None:
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            result = cli.main(("search", "STONE", "--file", str(self.index_path), "--limit", "1"))

        self.assertEqual(0, result)
        self.assertIn("entries: 2", stdout.getvalue())
        self.assertIn("1. ", stdout.getvalue())
        self.assertNotIn("2. ", stdout.getvalue())

    def test_cli_export_writes_csv(self) -> None:
        output_path = Path(self.temp_dir.name) / "stone.csv"

        result = cli.main(("export", "--file", str(self.index_path), "--identifier", "STONE",
                           "--output", str(output_path)))

        self.assertEqual(0, result)
        self.assertEqual(3, len(output_path.read_text(encoding="utf-8").splitlines()))

    def test_cli_returns_zero_when_search_has_no_matches(self) -> None:
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            result = cli.main(("search", "DIAMOND", "--file", str(self.index_path)))

        self.assertEqual(0, result)
        self.assertIn("entries: 0", stdout.getvalue())

    def test_cli_returns_one_for_invalid_index(self) -> None:
        self.index_path.write_bytes(b"invalid")
        stderr = io.StringIO()

        with contextlib.redirect_stderr(stderr):
            result = cli.main(("inspect", "--file", str(self.index_path)))

        self.assertEqual(1, result)
        self.assertIn("error:", stderr.getvalue())

    def test_viewer_api_uses_shared_query(self) -> None:
        server = viewer.ViewerServer(("127.0.0.1", 0), viewer.Handler)
        self.addCleanup(server.server_close)
        server.default_path = self.index_path
        server.world_map = {str(WORLD_ID): "world"}
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.shutdown)

        url = f"http://127.0.0.1:{server.server_port}/api/entries?identifier=STONE"
        with urllib.request.urlopen(url) as response:
            payload = json.load(response)

        self.assertEqual(2, payload["summary"]["count"])
        self.assertEqual("world", payload["entries"][0]["world_name"])

    def test_viewer_restricts_path_validates_mode_and_pages_results(self) -> None:
        server = viewer.ViewerServer(("127.0.0.1", 0), viewer.Handler)
        self.addCleanup(server.server_close)
        server.default_path = self.index_path
        server.world_map = {}
        server.max_results = 1
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.shutdown)
        base = f"http://127.0.0.1:{server.server_port}"

        with urllib.request.urlopen(base + "/api/entries?page=2&page_size=50") as response:
            payload = json.load(response)
        self.assertEqual(3, payload["summary"]["count"])
        self.assertEqual(1, payload["page_size"])
        self.assertEqual(1, len(payload["entries"]))

        for query in ("mode=invalid", "path=/etc/passwd", "page=0", "page_size=bad"):
            with self.subTest(query=query), self.assertRaises(urllib.error.HTTPError) as caught:
                urllib.request.urlopen(base + "/api/entries?" + query)
            self.assertEqual(400, caught.exception.code)

    def test_viewer_csv_and_not_found_endpoints(self) -> None:
        server = viewer.ViewerServer(("127.0.0.1", 0), viewer.Handler)
        self.addCleanup(server.server_close)
        server.default_path = self.index_path
        server.world_map = {}
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.shutdown)
        base = f"http://127.0.0.1:{server.server_port}"

        with urllib.request.urlopen(base + "/api/export.csv?identifier=STONE") as response:
            self.assertEqual("text/csv; charset=utf-8", response.headers["Content-Type"])
            self.assertEqual(3, len(response.read().decode("utf-8").splitlines()))
        with self.assertRaises(urllib.error.HTTPError) as caught:
            urllib.request.urlopen(base + "/missing")
        self.assertEqual(404, caught.exception.code)

    def test_viewer_caches_unchanged_index_between_requests(self) -> None:
        server = viewer.ViewerServer(("127.0.0.1", 0), viewer.Handler)
        self.addCleanup(server.server_close)
        server.default_path = self.index_path
        server.world_map = {}
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.shutdown)
        url = f"http://127.0.0.1:{server.server_port}/api/entries"

        with mock.patch.object(viewer, "read_index", wraps=index.read_index) as reader:
            urllib.request.urlopen(url).read()
            urllib.request.urlopen(url).read()
            self.assertEqual(1, reader.call_count)
            write_index(self.index_path)
            self.index_path.touch()
            urllib.request.urlopen(url).read()
            self.assertEqual(2, reader.call_count)

    def test_offline_region_rebuild_writes_index_without_server(self) -> None:
        world_dir = Path(self.temp_dir.name) / "world"
        region_dir = world_dir / "region"
        region_dir.mkdir(parents=True)
        output = Path(self.temp_dir.name) / "rebuilt.bin"

        world_uuid = WORLD_ID
        (world_dir / "level.dat").write_bytes(_encode_nbt_file({
            "Data": {
                "WorldUUIDMost": _nbt_long(_to_signed_64(world_uuid.int >> 64)),
                "WorldUUIDLeast": _nbt_long(_to_signed_64(world_uuid.int & ((1 << 64) - 1))),
            }
        }))
        (region_dir / "r.0.0.mca").write_bytes(_build_region_file())
        (region_dir / "r.1.0.mca").write_bytes(b"bad")

        result = region.write_rebuilt_index(output, [world_dir])
        entries = index.read_index(output)

        self.assertGreater(result, 0)
        self.assertEqual(1, len(entries))
        self.assertEqual(str(world_uuid), entries[0].world_id)
        self.assertEqual(1, entries[0].x)
        self.assertEqual(64, entries[0].y)
        self.assertEqual(1, entries[0].z)
        self.assertEqual("STONE", entries[0].identifier)
        self.assertEqual(128, entries[0].amount)
        self.assertEqual("NORTH", entries[0].front_facing)

    def test_offline_region_rebuild_prefers_persistent_identifier(self) -> None:
        full_identifier = "NETHERITE_UPGRADE_SMITHING_TEMPLATE"
        entries = region.scan_chunk({
            "block_entities": [{
                "id": "minecraft:sign",
                "x": 1,
                "y": 64,
                "z": 1,
                "front_text": {
                    "messages": ["StorageSign", "N:SMITHING_TEMPLATE", "128", ""],
                },
                "PublicBukkitValues": {
                    "storagesign:storage_identifier": full_identifier,
                },
            }],
        }, str(WORLD_ID))

        self.assertEqual(1, len(entries))
        self.assertEqual(full_identifier, entries[0].identifier)

    def test_offline_region_rebuild_falls_back_to_legacy_potion_identifier(self) -> None:
        entries = region.scan_chunk({
            "block_entities": [{
                "id": "minecraft:sign",
                "x": 1,
                "y": 64,
                "z": 1,
                "front_text": {
                    "messages": ["StorageSign", "POTION:HEAL:0", "4", ""],
                },
                "BukkitValues": {
                    "storagesign:potion_identifier": "POTION:minecraft:healing",
                },
            }],
        }, str(WORLD_ID))

        self.assertEqual("POTION:minecraft:healing", entries[0].identifier)

    def test_offline_region_rebuild_reads_legacy_tile_entities(self) -> None:
        entries = region.scan_chunk({
            "Level": {
                "TileEntities": [{
                    "id": "minecraft:sign",
                    "x": 1,
                    "y": 64,
                    "z": 1,
                    "Text1": json.dumps({"text": "StorageSign"}),
                    "Text2": json.dumps({"text": "STONE"}),
                    "Text3": json.dumps({"text": "12"}),
                    "Text4": json.dumps({"text": ""}),
                }],
            },
        }, str(WORLD_ID))

        self.assertEqual(1, len(entries))
        self.assertEqual("STONE", entries[0].identifier)
        self.assertEqual(12, entries[0].amount)

    def test_offline_region_rebuild_skips_invalid_chunk_pointer(self) -> None:
        world_dir = Path(self.temp_dir.name) / "world"
        region_dir = world_dir / "region"
        region_dir.mkdir(parents=True)
        output = Path(self.temp_dir.name) / "rebuilt.bin"

        world_uuid = WORLD_ID
        (world_dir / "uid.dat").write_bytes(world_uuid.bytes)
        region_bytes = bytearray(_build_region_file())
        region_bytes[4:8] = struct.pack(">I", (12 << 8) | 1)
        (region_dir / "r.0.0.mca").write_bytes(region_bytes)

        warnings: list[str] = []
        result = region.write_rebuilt_index(output, [world_dir], warn=warnings.append)
        entries = index.read_index(output)

        self.assertGreater(result, 0)
        self.assertEqual(1, len(entries))
        self.assertEqual(str(world_uuid), entries[0].world_id)
        self.assertTrue(any("chunk extends past end of region file" in warning for warning in warnings))

    def test_offline_region_rebuild_skips_missing_world_directory(self) -> None:
        valid_world = Path(self.temp_dir.name) / "world"
        missing_world = Path(self.temp_dir.name) / "missing"
        region_dir = valid_world / "region"
        region_dir.mkdir(parents=True)
        output = Path(self.temp_dir.name) / "rebuilt.bin"

        world_uuid = WORLD_ID
        (valid_world / "uid.dat").write_bytes(world_uuid.bytes)
        (region_dir / "r.0.0.mca").write_bytes(_build_region_file())

        warnings: list[str] = []
        result = region.write_rebuilt_index(output, [missing_world, valid_world], warn=warnings.append)
        entries = index.read_index(output)

        self.assertGreater(result, 0)
        self.assertEqual(1, len(entries))
        self.assertEqual(str(world_uuid), entries[0].world_id)
        self.assertTrue(any("skipping world" in warning for warning in warnings))

    def test_offline_region_rebuild_rejects_all_missing_worlds(self) -> None:
        output = Path(self.temp_dir.name) / "rebuilt.bin"

        with self.assertRaises(ValueError):
            region.write_rebuilt_index(
                output,
                [Path(self.temp_dir.name) / "missing-a", Path(self.temp_dir.name) / "missing-b"],
                warn=lambda message: None,
            )

    def test_offline_region_rebuild_cli_uses_repo_root_output_by_default(self) -> None:
        world_dir = Path(self.temp_dir.name) / "world"
        region_dir = world_dir / "region"
        region_dir.mkdir(parents=True)
        (world_dir / "uid.dat").write_bytes(WORLD_ID.bytes)
        (region_dir / "r.0.0.mca").write_bytes(_build_region_file())

        captured: dict[str, object] = {}

        def fake_write(output: Path, world_dirs: list[Path], warn) -> int:
            captured["output"] = output
            captured["world_dirs"] = list(world_dirs)
            return 3

        with mock.patch.object(region_cli, "write_rebuilt_index", side_effect=fake_write):
            with contextlib.redirect_stdout(io.StringIO()):
                code = region_cli.main([str(world_dir)])

        self.assertEqual(0, code)
        self.assertEqual(TOOLS_DIR.parent / "plugins/StorageSign-Refactored/storage-sign-index.bin",
                         captured["output"])
        self.assertEqual([world_dir], captured["world_dirs"])

    def test_offline_region_rebuild_cli_accepts_legacy_rebuild_alias(self) -> None:
        world_dir = Path(self.temp_dir.name) / "world"
        region_dir = world_dir / "region"
        region_dir.mkdir(parents=True)
        (world_dir / "uid.dat").write_bytes(WORLD_ID.bytes)
        (region_dir / "r.0.0.mca").write_bytes(_build_region_file())

        captured: dict[str, object] = {}

        def fake_write(output: Path, world_dirs: list[Path], warn) -> int:
            captured["output"] = output
            captured["world_dirs"] = list(world_dirs)
            return 2

        with mock.patch.object(region_cli, "write_rebuilt_index", side_effect=fake_write):
            with contextlib.redirect_stdout(io.StringIO()):
                code = region_cli.main(["rebuild", str(world_dir)])

        self.assertEqual(0, code)
        self.assertEqual([world_dir], captured["world_dirs"])

    def test_offline_region_rebuild_cli_returns_nonzero_when_warnings_are_emitted(self) -> None:
        world_dir = Path(self.temp_dir.name) / "world"
        region_dir = world_dir / "region"
        region_dir.mkdir(parents=True)
        (world_dir / "uid.dat").write_bytes(WORLD_ID.bytes)
        (region_dir / "r.0.0.mca").write_bytes(_build_region_file())

        def fake_write(output: Path, world_dirs: list[Path], warn) -> int:
            warn("warning: synthetic issue")
            return 1

        with mock.patch.object(region_cli, "write_rebuilt_index", side_effect=fake_write):
            with contextlib.redirect_stderr(io.StringIO()) as stderr, contextlib.redirect_stdout(io.StringIO()):
                code = region_cli.main([str(world_dir)])

        self.assertEqual(1, code)
        self.assertIn("warning: synthetic issue", stderr.getvalue())
        self.assertIn("warning: rebuild completed with warnings", stderr.getvalue())

    def test_offline_region_rebuild_prefers_uid_dat_from_repo_fixture(self) -> None:
        world_dir = Path("e2e/runtime/1.21.4/data/world")
        output = Path(self.temp_dir.name) / "fixture-rebuilt.bin"

        world_uuid = region.read_world_uuid(world_dir)
        warnings: list[str] = []
        result = region.write_rebuilt_index(output, [world_dir], warn=warnings.append)
        entries = index.read_index(output)

        self.assertEqual(uuid.UUID(bytes=(world_dir / "uid.dat").read_bytes()), world_uuid)
        self.assertEqual(0, result)
        self.assertEqual([], entries)
        self.assertEqual([], warnings)


def _build_region_file() -> bytes:
    indices = [0] * 4096
    indices[(0 << 8) | (1 << 4) | 1] = 1
    chunk = _encode_nbt_bytes({
        "DataVersion": _nbt_int(4189),
        "xPos": _nbt_int(0),
        "zPos": _nbt_int(0),
        "LastUpdate": _nbt_long(1_700_000_000_000),
        "sections": _nbt_list("compound", [
            {
                "Y": _nbt_byte(4),
                "block_states": {
                    "palette": _nbt_list("compound", [
                        {"Name": _nbt_string("minecraft:air")},
                        {
                            "Name": _nbt_string("minecraft:oak_wall_sign"),
                            "Properties": {"facing": _nbt_string("north")},
                        },
                    ]),
                    "data": _nbt_long_array(_pack_palette_indices(indices, 4)),
                },
            }
        ]),
        "block_entities": _nbt_list("compound", [
            {
                "id": _nbt_string("minecraft:sign"),
                "x": _nbt_int(1),
                "y": _nbt_int(64),
                "z": _nbt_int(1),
                "front_text": {
                    "messages": _nbt_list("string", [
                        _nbt_string('{"text":"StorageSign"}'),
                        _nbt_string('{"text":"STONE"}'),
                        _nbt_string('{"text":"128"}'),
                        _nbt_string('{"text":""}'),
                    ]),
                },
            }
        ]),
    })

    region_bytes = io.BytesIO()
    compressed = zlib.compress(chunk)
    chunk_record = struct.pack(">I", len(compressed) + 1) + b"\x02" + compressed
    sector_count = (len(chunk_record) + SECTOR_BYTES - 1) // SECTOR_BYTES
    header = bytearray(8192)
    header[0:4] = struct.pack(">I", (2 << 8) | sector_count)
    header[4096:4100] = struct.pack(">I", 1_700_000_000)
    region_bytes.write(header)
    region_bytes.write(chunk_record)
    region_bytes.write(b"\x00" * ((-region_bytes.tell()) % SECTOR_BYTES))
    return region_bytes.getvalue()


def _encode_nbt_file(root: dict[str, object]) -> bytes:
    return gzip.compress(_encode_nbt_bytes(root))


def _encode_nbt_bytes(root: dict[str, object]) -> bytes:
    buffer = io.BytesIO()
    buffer.write(b"\x0a")
    buffer.write(struct.pack(">H", 0))
    _write_compound_payload(buffer, root)
    return buffer.getvalue()


def _write_compound_payload(buffer: io.BytesIO, value: dict[str, object]) -> None:
    for key, item in value.items():
        tag_type, payload = _unwrap_tag(item)
        buffer.write(struct.pack(">B", tag_type))
        encoded_key = key.encode("utf-8")
        buffer.write(struct.pack(">H", len(encoded_key)))
        buffer.write(encoded_key)
        _write_tag_payload(buffer, tag_type, payload)
    buffer.write(b"\x00")


def _write_tag_payload(buffer: io.BytesIO, tag_type: int, payload: object) -> None:
    if tag_type == 1:
        buffer.write(struct.pack(">b", int(payload)))
    elif tag_type == 3:
        buffer.write(struct.pack(">i", int(payload)))
    elif tag_type == 4:
        buffer.write(struct.pack(">q", int(payload)))
    elif tag_type == 8:
        encoded = str(payload).encode("utf-8")
        buffer.write(struct.pack(">H", len(encoded)))
        buffer.write(encoded)
    elif tag_type == 9:
        item_type, items = payload
        buffer.write(struct.pack(">B", item_type))
        buffer.write(struct.pack(">i", len(items)))
        for item in items:
            if isinstance(item, tuple) and item and item[0] in {"byte", "int", "long", "string", "compound", "long_array"}:
                _, item = _unwrap_tag(item)
            _write_tag_payload(buffer, item_type, item)
    elif tag_type == 10:
        _write_compound_payload(buffer, payload)
    elif tag_type == 12:
        items = list(payload)
        buffer.write(struct.pack(">i", len(items)))
        for item in items:
            buffer.write(struct.pack(">q", int(item)))
    else:
        raise AssertionError(f"unsupported test tag type: {tag_type}")


def _unwrap_tag(value: object) -> tuple[int, object]:
    if isinstance(value, tuple) and value:
        kind = value[0]
        if kind == "byte":
            return 1, value[1]
        if kind == "int":
            return 3, value[1]
        if kind == "long":
            return 4, value[1]
        if kind == "string":
            return 8, value[1]
        if kind == "list":
            item_type = {"byte": 1, "int": 3, "long": 4, "string": 8, "compound": 10, "long_array": 12}[value[1]]
            return 9, (item_type, value[2])
        if kind == "compound":
            return 10, value[1]
        if kind == "long_array":
            return 12, value[1]
    if isinstance(value, dict):
        return 10, value
    if isinstance(value, int):
        return 3, value
    if isinstance(value, str):
        return 8, value
    if isinstance(value, list):
        return 9, (8, value)
    raise AssertionError(f"unsupported test tag value: {value!r}")


def _nbt_byte(value: int) -> tuple[str, int]:
    return "byte", value


def _nbt_int(value: int) -> tuple[str, int]:
    return "int", value


def _nbt_long(value: int) -> tuple[str, int]:
    return "long", value


def _to_signed_64(value: int) -> int:
    value &= (1 << 64) - 1
    if value >= (1 << 63):
        value -= 1 << 64
    return value


def _nbt_string(value: str) -> tuple[str, str]:
    return "string", value


def _nbt_list(kind: str, items: list[object]) -> tuple[str, str, list[object]]:
    return "list", kind, items


def _nbt_long_array(items: list[int]) -> tuple[str, list[int]]:
    return "long_array", items


def _pack_palette_indices(indices: list[int], bits: int) -> list[int]:
    values_per_long = 64 // bits
    longs = [0] * ((len(indices) + values_per_long - 1) // values_per_long)
    for index, value in enumerate(indices):
        long_index = index // values_per_long
        bit_index = (index % values_per_long) * bits
        longs[long_index] |= (int(value) & ((1 << bits) - 1)) << bit_index
    return longs


if __name__ == "__main__":
    unittest.main()
