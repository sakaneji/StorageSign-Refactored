#!/usr/bin/env python3
"""Read and browse the persistent StorageSign index.

This tool understands the binary format written by StorageSignIndexCodec and
can either print query results or serve a small local web UI for searching.
"""

from __future__ import annotations

import argparse
import csv
import html
import io
import json
import struct
import sys
import urllib.parse
import uuid
import zlib
from collections import defaultdict
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Sequence


MAGIC = 0x53534958
VERSION = 1
MAX_IDENTIFIER_BYTES = 65_536
DEFAULT_INDEX_PATHS = (
    Path("plugins/StorageSign-Refactored/storage-sign-index.bin"),
    Path("plugins/StorageSign-Refactored/storage-sign-index.bin.tmp"),
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

    @property
    def world_uuid(self) -> str:
        return self.world_id

    @property
    def chunk_x(self) -> int:
        return self.x >> 4

    @property
    def chunk_z(self) -> int:
        return self.z >> 4

    @property
    def location(self) -> str:
        return f"{self.x}, {self.y}, {self.z}"


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
    magic, version, count = struct.unpack(">III", stream.read(12))
    if magic != MAGIC:
        raise ValueError("invalid index magic")
    if version != VERSION:
        raise ValueError(f"unsupported index version: {version}")
    if count < 0:
        raise ValueError(f"invalid entry count: {count}")

    entries: list[Entry] = []
    for _ in range(count):
        world_msb, world_lsb = struct.unpack(">qq", _read_exact(stream, 16))
        x, y, z, amount = struct.unpack(">iiii", _read_exact(stream, 16))
        (verified_at,) = struct.unpack(">q", _read_exact(stream, 8))
        (identifier_length,) = struct.unpack(">i", _read_exact(stream, 4))
        if identifier_length <= 0 or identifier_length > MAX_IDENTIFIER_BYTES:
            raise ValueError(f"invalid identifier length: {identifier_length}")
        identifier_bytes = _read_exact(stream, identifier_length)
        world_uuid = _format_uuid(world_msb, world_lsb)
        identifier = identifier_bytes.decode("utf-8")
        entries.append(Entry(world_uuid, x, y, z, identifier, amount, verified_at))

    if stream.read(1):
        raise ValueError("unexpected trailing index data")
    return entries


def _format_uuid(msb: int, lsb: int) -> str:
    return str(uuid.UUID(bytes=struct.pack(">qq", msb, lsb)))


def _read_exact(stream: io.BytesIO, size: int) -> bytes:
    data = stream.read(size)
    if len(data) != size:
        raise ValueError("index file is truncated")
    return data


def matches(entry: Entry, identifier: str | None, mode: str, world: str | None) -> bool:
    if world and entry.world_id.lower() != world.lower():
        return False
    if not identifier:
        return True
    haystack = entry.identifier.lower()
    needle = identifier.lower()
    if mode == "contains":
        return needle in haystack
    return haystack == needle


def filter_entries(entries: Sequence[Entry], identifier: str | None, mode: str, world: str | None) -> list[Entry]:
    return [entry for entry in entries if matches(entry, identifier, mode, world)]


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
        "worlds": dict(sorted(by_world.items(), key=lambda item: item[0])),
        "mapped_worlds": len(mapped_worlds),
    }


def world_label(world_id: str, world_map: WorldMap | None = None) -> str:
    if world_map:
        name = world_map.get(world_id)
        if name:
            return f"{name} ({world_id})"
    return world_id


def cli_rows(entries: Sequence[Entry], limit: int | None = None, world_map: WorldMap | None = None) -> str:
    shown = entries if limit is None else entries[:limit]
    lines = []
    for index, entry in enumerate(shown, 1):
        lines.append(
            f"{index}. {world_label(entry.world_id, world_map)} {entry.x} {entry.y} {entry.z} "
            f"— {entry.amount} — {entry.identifier} [verified={entry.verified_at}]"
        )
    return "\n".join(lines)


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
    }


def csv_rows(entries: Sequence[Entry], world_map: WorldMap | None = None) -> str:
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow([
        "world_id",
        "world_name",
        "x",
        "y",
        "z",
        "identifier",
        "amount",
        "verified_at",
    ])
    for entry in entries:
        writer.writerow([
            entry.world_id,
            world_map.get(entry.world_id) if world_map else "",
            entry.x,
            entry.y,
            entry.z,
            entry.identifier,
            entry.amount,
            entry.verified_at,
        ])
    return buffer.getvalue()


HTML_TEMPLATE = """<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>StorageSign Index Viewer</title>
  <style>
    :root {{
      color-scheme: light;
      --bg: #0f172a;
      --panel: #111827;
      --panel-soft: #1f2937;
      --text: #e5e7eb;
      --muted: #9ca3af;
      --accent: #38bdf8;
      --accent-2: #a78bfa;
      --border: rgba(148, 163, 184, 0.22);
      --shadow: 0 18px 50px rgba(15, 23, 42, 0.32);
      --radius: 18px;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      color: var(--text);
      background:
        radial-gradient(circle at top left, rgba(56, 189, 248, 0.18), transparent 28%),
        radial-gradient(circle at top right, rgba(167, 139, 250, 0.16), transparent 26%),
        linear-gradient(180deg, #020617 0%, var(--bg) 100%);
      min-height: 100vh;
    }}
    main {{
      max-width: 1200px;
      margin: 0 auto;
      padding: 32px 20px 48px;
    }}
    header {{
      display: grid;
      gap: 10px;
      margin-bottom: 24px;
    }}
    h1 {{
      margin: 0;
      font-size: clamp(2rem, 4vw, 3.2rem);
      letter-spacing: -0.04em;
    }}
    .subtitle {{
      margin: 0;
      color: var(--muted);
      max-width: 68ch;
      line-height: 1.6;
    }}
    .grid {{
      display: grid;
      grid-template-columns: 320px 1fr;
      gap: 18px;
    }}
    .card {{
      background: rgba(17, 24, 39, 0.82);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      box-shadow: var(--shadow);
      backdrop-filter: blur(10px);
    }}
    .controls, .stats, .table-wrap {{
      padding: 18px;
    }}
    .controls {{
      display: grid;
      gap: 12px;
      align-content: start;
    }}
    label {{
      display: grid;
      gap: 6px;
      font-size: 0.9rem;
      color: var(--muted);
    }}
    input, select, button {{
      font: inherit;
      color: var(--text);
      background: rgba(15, 23, 42, 0.9);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 10px 12px;
    }}
    input::placeholder {{ color: #64748b; }}
    button {{
      cursor: pointer;
      background: linear-gradient(135deg, var(--accent), var(--accent-2));
      color: #020617;
      font-weight: 700;
      border: none;
    }}
    button.secondary {{
      background: rgba(15, 23, 42, 0.9);
      color: var(--text);
      border: 1px solid var(--border);
    }}
    .stats {{
      display: grid;
      gap: 10px;
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }}
    .stat {{
      background: rgba(15, 23, 42, 0.8);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 14px;
    }}
    .stat .k {{
      display: block;
      color: var(--muted);
      font-size: 0.82rem;
      margin-bottom: 6px;
    }}
    .stat .v {{
      font-size: 1.15rem;
      font-weight: 700;
    }}
    .table-wrap {{
      overflow: hidden;
    }}
    table {{
      width: 100%;
      border-collapse: collapse;
      min-width: 720px;
    }}
    th, td {{
      text-align: left;
      padding: 10px 8px;
      border-bottom: 1px solid rgba(148, 163, 184, 0.14);
      vertical-align: top;
    }}
    th {{
      color: #cbd5e1;
      font-size: 0.84rem;
      letter-spacing: 0.02em;
      text-transform: uppercase;
    }}
    tbody tr:hover {{
      background: rgba(148, 163, 184, 0.06);
    }}
    .muted {{ color: var(--muted); }}
    .pill {{
      display: inline-block;
      padding: 3px 8px;
      border-radius: 999px;
      background: rgba(56, 189, 248, 0.15);
      color: #7dd3fc;
      font-size: 0.82rem;
    }}
    .footer {{
      margin-top: 12px;
      color: var(--muted);
      font-size: 0.9rem;
      line-height: 1.5;
    }}
    @media (max-width: 980px) {{
      .grid {{
        grid-template-columns: 1fr;
      }}
      .stats {{
        grid-template-columns: 1fr;
      }}
      table {{
        min-width: 0;
      }}
    }}
  </style>
</head>
<body>
  <main>
    <header>
      <h1>StorageSign Index Viewer</h1>
      <p class="subtitle">
        保存済みの `storage-sign-index.bin` を読み出して検索するローカルビューアです。
        World UUID, 座標, 数量, 識別子, 最終確認時刻をブラウザで確認できます。
      </p>
    </header>

    <section class="grid">
      <div class="card controls">
        <label>
          Index file
          <input id="path" value="__INDEX_PATH__" spellcheck="false">
        </label>
        <label>
          Identifier
          <input id="identifier" placeholder="STONE">
        </label>
        <label>
          Match mode
          <select id="mode">
            <option value="exact">exact</option>
            <option value="contains">contains</option>
          </select>
        </label>
        <label>
          World UUID
          <input id="world" placeholder="optional">
        </label>
        <div style="display:flex; gap:10px; flex-wrap:wrap;">
          <button id="search">Search</button>
          <button class="secondary" id="reset" type="button">Reset</button>
        </div>
      </div>

      <div class="card">
        <div class="stats">
          <div class="stat"><span class="k">Entries</span><span class="v" id="stat-count">-</span></div>
          <div class="stat"><span class="k">Total amount</span><span class="v" id="stat-amount">-</span></div>
          <div class="stat"><span class="k">Worlds</span><span class="v" id="stat-worlds">-</span></div>
          <div class="stat"><span class="k">Mapped worlds</span><span class="v" id="stat-mapped-worlds">-</span></div>
        </div>
        <div style="padding: 0 18px 12px; display:flex; gap:10px; flex-wrap:wrap;">
          <button class="secondary" id="export-csv" type="button">Download CSV</button>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>World UUID</th>
                <th>World Name</th>
                <th>Position</th>
                <th>Identifier</th>
                <th>Amount</th>
                <th>Verified</th>
              </tr>
            </thead>
            <tbody id="rows"></tbody>
          </table>
        </div>
        <div class="footer" id="status">Loading...</div>
      </div>
    </section>
  </main>
  <script>
    const rows = document.getElementById("rows");
    const status = document.getElementById("status");
    const statCount = document.getElementById("stat-count");
    const statAmount = document.getElementById("stat-amount");
    const statWorlds = document.getElementById("stat-worlds");
    const statMappedWorlds = document.getElementById("stat-mapped-worlds");
    const fields = {{
      path: document.getElementById("path"),
      identifier: document.getElementById("identifier"),
      mode: document.getElementById("mode"),
      world: document.getElementById("world"),
    }};

    async function refresh() {{
      const params = new URLSearchParams({{
        path: fields.path.value,
        identifier: fields.identifier.value,
        mode: fields.mode.value,
        world: fields.world.value,
      }});
      status.textContent = "Loading...";
      const response = await fetch(`/api/entries?${{params.toString()}}`);
      const data = await response.json();
      if (!response.ok) {{
        status.textContent = data.error || "Request failed";
        rows.innerHTML = "";
        return;
      }}
      statCount.textContent = data.summary.count;
      statAmount.textContent = data.summary.total_amount;
      statWorlds.textContent = Object.keys(data.summary.worlds).length;
      statMappedWorlds.textContent = data.summary.mapped_worlds || 0;
      status.textContent = `Showing ${{data.entries.length}} of ${{data.summary.count}} entries from ${{data.path}}`;
      rows.innerHTML = data.entries.map((entry, index) => `
        <tr>
          <td>${{index + 1}}</td>
          <td><span class="pill">${{entry.world_id}}</span></td>
          <td>${{entry.world_name ? escapeHtml(entry.world_name) : '<span class="muted">-</span>'}}</td>
          <td>${{entry.x}}, ${{entry.y}}, ${{entry.z}}</td>
          <td>${{escapeHtml(entry.identifier)}}</td>
          <td>${{entry.amount}}</td>
          <td class="muted">${{new Date(entry.verified_at).toLocaleString()}}</td>
        </tr>
      `).join("");
    }}

    function escapeHtml(value) {{
      return value.replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
    }}

    document.getElementById("search").addEventListener("click", (event) => {{
      event.preventDefault();
      refresh();
    }});
    document.getElementById("reset").addEventListener("click", () => {{
      fields.identifier.value = "";
      fields.mode.value = "exact";
      fields.world.value = "";
      refresh();
    }});
    document.getElementById("export-csv").addEventListener("click", () => {{
      const params = new URLSearchParams({{
        path: fields.path.value,
        identifier: fields.identifier.value,
        mode: fields.mode.value,
        world: fields.world.value,
      }});
      window.location.href = `/api/export.csv?${{params.toString()}}`;
    }});
    for (const field of Object.values(fields)) {{
      field.addEventListener("keydown", (event) => {{
        if (event.key === "Enter") refresh();
      }});
    }}
    refresh().catch((error) => {{
      status.textContent = error.message;
    }});
  </script>
</body>
</html>
"""


class ViewerServer(ThreadingHTTPServer):
    daemon_threads = True


class Handler(BaseHTTPRequestHandler):
    server_version = "StorageSignIndexViewer/1.0"

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/api/entries":
            self._serve_api(parsed)
            return
        if parsed.path == "/api/export.csv":
            self._serve_csv(parsed)
            return
        if parsed.path in {"/", "/index.html"}:
            self._serve_index()
            return
        self.send_error(HTTPStatus.NOT_FOUND, "Not found")

    def log_message(self, fmt: str, *args) -> None:  # noqa: A003
        return

    def _serve_index(self) -> None:
        path = self.server.default_path
        html_doc = HTML_TEMPLATE.replace("__INDEX_PATH__", html.escape(str(path)))
        body = html_doc.encode("utf-8")
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _serve_api(self, parsed: urllib.parse.ParseResult) -> None:
        query = urllib.parse.parse_qs(parsed.query)
        path_value = query.get("path", [str(self.server.default_path)])[0]
        identifier = query.get("identifier", [""])[0].strip() or None
        mode = query.get("mode", ["exact"])[0].strip().lower()
        world = query.get("world", [""])[0].strip() or None
        try:
            path = Path(path_value).expanduser()
            entries = read_index(path)
            filtered = filter_entries(entries, identifier, mode, world)
            payload = {
                "path": str(path),
                "summary": summarize(filtered, self.server.world_map),
                "entries": [entry_payload(entry, self.server.world_map) for entry in filtered],
            }
            body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except Exception as exc:  # noqa: BLE001
            body = json.dumps({"error": str(exc)}).encode("utf-8")
            self.send_response(HTTPStatus.BAD_REQUEST)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    def _serve_csv(self, parsed: urllib.parse.ParseResult) -> None:
        query = urllib.parse.parse_qs(parsed.query)
        path_value = query.get("path", [str(self.server.default_path)])[0]
        identifier = query.get("identifier", [""])[0].strip() or None
        mode = query.get("mode", ["exact"])[0].strip().lower()
        world = query.get("world", [""])[0].strip() or None
        try:
            path = Path(path_value).expanduser()
            entries = filter_entries(read_index(path), identifier, mode, world)
            body = csv_rows(entries, self.server.world_map).encode("utf-8")
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header("Content-Disposition", 'attachment; filename="storage-sign-index.csv"')
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except Exception as exc:  # noqa: BLE001
            body = json.dumps({"error": str(exc)}).encode("utf-8")
            self.send_response(HTTPStatus.BAD_REQUEST)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", type=Path, default=default_index_path(),
                        help="path to storage-sign-index.bin")
    parser.add_argument("--world-map", type=Path, default=None,
                        help="JSON or CSV file mapping world UUID to world name")
    parser.add_argument("--identifier", default="", help="identifier to search for")
    parser.add_argument("--mode", choices=("exact", "contains"), default="exact")
    parser.add_argument("--world", default="", help="world UUID filter")
    parser.add_argument("--limit", type=int, default=50, help="limit for CLI output")
    parser.add_argument("--format", choices=("table", "json", "csv", "html"), default="table")
    parser.add_argument("--serve", action="store_true", help="start local HTTP viewer")
    parser.add_argument("--host", default="127.0.0.1", help="HTTP host when serving")
    parser.add_argument("--port", type=int, default=8765, help="HTTP port when serving")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    world_map = load_world_map(args.world_map)
    if args.serve:
        server = ViewerServer((args.host, args.port), Handler)
        server.default_path = args.file
        server.world_map = world_map
        print(f"Serving {args.file} at http://{args.host}:{args.port}/")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            return 130
        return 0

    entries = read_index(args.file)
    filtered = filter_entries(entries, args.identifier or None, args.mode, args.world or None)
    summary = summarize(filtered, world_map)

    if args.format == "json":
        print(json.dumps({
            "path": str(args.file),
            "summary": summary,
            "entries": [entry_payload(entry, world_map) for entry in filtered[: args.limit]],
        }, ensure_ascii=False, indent=2))
        return 0

    if args.format == "csv":
        sys.stdout.write(csv_rows(filtered, world_map))
        return 0

    if args.format == "html":
        print(HTML_TEMPLATE.replace("__INDEX_PATH__", html.escape(str(args.file))))
        return 0

    print(f"path: {args.file}")
    print(f"entries: {summary['count']}")
    print(f"totalAmount: {summary['total_amount']}")
    print(f"worlds: {len(summary['worlds'])}")
    print(f"mappedWorlds: {summary['mapped_worlds']}")
    print(cli_rows(filtered, args.limit, world_map))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
