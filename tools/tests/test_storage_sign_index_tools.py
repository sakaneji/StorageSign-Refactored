from __future__ import annotations

import contextlib
import base64
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


WORLD_ID = uuid.UUID("12345678-1234-5678-9abc-def012345678")
OTHER_WORLD_ID = uuid.UUID("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")


def write_index(path: Path) -> None:
    entries = (
        (WORLD_ID, 10, 64, -3, "STONE", 128, 1_700_000_000_000),
        (WORLD_ID, 11, 65, -4, "POTION:HEAL:0", 4, 1_700_000_001_000),
        (OTHER_WORLD_ID, 20, 70, 30, "STONE", 32, 1_700_000_002_000),
    )
    payload = io.BytesIO()
    payload.write(struct.pack(">III", index.MAGIC, index.VERSION, len(entries)))
    for world_id, x, y, z, identifier, amount, verified_at in entries:
        world_msb, world_lsb = struct.unpack(">qq", world_id.bytes)
        encoded_identifier = identifier.encode("utf-8")
        payload.write(struct.pack(">qqiiiiqi", world_msb, world_lsb, x, y, z, amount, verified_at,
                                  len(encoded_identifier)))
        payload.write(encoded_identifier)
    raw = payload.getvalue()
    path.write_bytes(raw + struct.pack(">I", zlib.crc32(raw) & 0xFFFFFFFF))


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
        payload = struct.pack(">IIIqqiiiiqi", index.MAGIC, index.VERSION, 1,
                              world_msb, world_lsb, 0, 0, 0, 1, 0, 1) + b"\xff"
        write_payload(self.index_path, payload)
        with self.assertRaises(UnicodeDecodeError):
            index.read_index(self.index_path)

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


if __name__ == "__main__":
    unittest.main()
