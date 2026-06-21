#!/usr/bin/env python3
"""Inspect, search, and export a persistent StorageSign index."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

from storage_sign_index import (
    csv_result,
    default_index_path,
    filter_entries,
    json_result,
    load_world_map,
    read_index,
    text_result,
    text_summary,
)


def add_source_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--file", type=Path, default=default_index_path(),
                        help="path to storage-sign-index.bin")
    parser.add_argument("--world-map", type=Path,
                        help="JSON or CSV file mapping world UUID to world name")


def add_filter_arguments(parser: argparse.ArgumentParser, identifier_optional: bool) -> None:
    if identifier_optional:
        parser.add_argument("--identifier", help="identifier to filter")
    parser.add_argument("--contains", action="store_true", help="use case-insensitive partial matching")
    parser.add_argument("--world", help="world UUID filter")


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    inspect_parser = subparsers.add_parser("inspect", help="show index summary")
    add_source_arguments(inspect_parser)
    inspect_parser.add_argument("--format", choices=("text", "json"), default="text")

    search_parser = subparsers.add_parser("search", help="search for an item identifier")
    search_parser.add_argument("identifier", help="identifier to search for")
    add_source_arguments(search_parser)
    add_filter_arguments(search_parser, identifier_optional=False)
    search_parser.add_argument("--limit", type=_non_negative_int, default=50)
    search_parser.add_argument("--format", choices=("text", "json"), default="text")

    export_parser = subparsers.add_parser("export", help="export index entries")
    add_source_arguments(export_parser)
    add_filter_arguments(export_parser, identifier_optional=True)
    export_parser.add_argument("--format", choices=("csv", "json"), default="csv")
    export_parser.add_argument("--output", type=Path, help="output file; omit for standard output")
    return parser.parse_args(argv)


def _non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be zero or greater")
    return parsed


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    try:
        world_map = load_world_map(args.world_map)
        entries = read_index(args.file)
        identifier = getattr(args, "identifier", None)
        entries = filter_entries(entries, identifier, getattr(args, "contains", False), getattr(args, "world", None))

        if args.command == "search":
            matched_entries = entries
            entries = matched_entries[:args.limit]
        else:
            matched_entries = entries

        if args.command == "inspect" and args.format == "text":
            output = text_summary(args.file, entries, world_map)
        elif args.command == "inspect":
            output = json_result(args.file, (), world_map, entries)
        elif args.command == "export" and args.format == "csv":
            output = csv_result(entries, world_map)
        elif args.format == "json":
            output = json_result(args.file, entries, world_map, matched_entries)
        else:
            output = text_result(args.file, entries, world_map, matched_entries)

        if getattr(args, "output", None):
            args.output.write_text(output, encoding="utf-8", newline="")
        else:
            sys.stdout.write(output)
            if output and not output.endswith("\n"):
                sys.stdout.write("\n")
        return 0
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
