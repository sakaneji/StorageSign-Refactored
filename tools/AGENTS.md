# Tooling Rules

Applies to external Python index/search/offline maintenance tools and `tools/tests`.

- Read `../docs/workflow-implementation.md`; also `../docs/workflow-testing.md` for tests.
- Read `../docs/storage-sign-index.md` for index/search/offline rebuild/viewer behavior.
- Read `../README.md` and `../docs/documentation-update-guide.md` for operator-facing changes.
- Keep standalone tooling separated from live Bukkit plugin code.
- Preserve the persistent index format and validate `SSIX` magic plus trailing CRC32 when reading index files.
- Treat server administrators as the primary users: keep invocation short, defaults stable, and warning-bearing partial success nonzero.
- Python tooling requires host `python3` 3.10 or newer.
- Prefer `../scripts/test.sh unit` for the standard lightweight verification path.
- Put short operator guidance in `../README.md` and detailed behavior in `../docs/storage-sign-index.md`.
