#!/usr/bin/env python3
"""Rebuild a persistent StorageSign index from offline Minecraft region files."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Sequence

from storage_sign_index import default_index_path
from storage_sign_region import write_rebuilt_index


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.epilog = "The legacy 'rebuild' prefix is accepted for compatibility."
    parser.add_argument("world_dirs", nargs="+", type=Path, help="world directories containing region/*.mca")
    parser.add_argument("--output", type=Path, default=default_index_path(),
                        help="output storage-sign-index.bin path")
    normalized = list(argv)
    if normalized and normalized[0].lower() == "rebuild":
        normalized = normalized[1:]
    return parser.parse_args(normalized)


def main(argv: Sequence[str] | None = None) -> int:
    try:
        args = parse_args(argv if argv is not None else sys.argv[1:])
        warnings_seen = False

        def warn(message: str) -> None:
            nonlocal warnings_seen
            warnings_seen = True
            print(message, file=sys.stderr)

        entry_count = write_rebuilt_index(args.output, args.world_dirs, warn=warn)
        sys.stdout.write(f"rebuilt {entry_count} entries in {args.output}\n")
        if warnings_seen:
            print("warning: rebuild completed with warnings", file=sys.stderr)
            return 1
        return 0
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
