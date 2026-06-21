#!/usr/bin/env python3
"""Serve a local web viewer for the persistent StorageSign index."""

from __future__ import annotations

import argparse
import html
import json
import sys
import urllib.parse
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Sequence

from storage_sign_index import (
    csv_result,
    default_index_path,
    entry_payload,
    filter_entries,
    load_world_map,
    read_index,
    summarize,
)


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
            filtered = filter_entries(entries, identifier, mode == "contains", world)
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
            entries = filter_entries(read_index(path), identifier, mode == "contains", world)
            body = csv_result(entries, self.server.world_map).encode("utf-8")
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
    parser.add_argument("--host", default="127.0.0.1", help="HTTP host when serving")
    parser.add_argument("--port", type=int, default=8765, help="HTTP port when serving")
    parser.add_argument("--serve", action="store_true", help=argparse.SUPPRESS)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    try:
        world_map = load_world_map(args.world_map)
        server = ViewerServer((args.host, args.port), Handler)
        server.default_path = args.file
        server.world_map = world_map
        print(f"Serving {args.file} at http://{args.host}:{args.port}/")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            return 130
        return 0
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
