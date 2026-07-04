# Implementation Rules

Applies to plugin source and packaged resources under `src/main`.

- Read `../../docs/workflow-implementation.md`, touched source, and related tests.
- For user-visible behavior/config/commands/permissions, read `../../README.md` and `../../docs/documentation-update-guide.md`.
- For index/search/`/sswarp`/nearby-display, read `../../docs/storage-sign-index.md`.
- Patch the smallest useful file set.
- Keep legacy and compatibility behavior explicit.
- Do not guess when code or tests can confirm behavior.
- If public behavior changes, update related docs in the same change; use `../../docs/AGENTS.md` for targets.
- If config defaults change, update `resources/config.default.yml` and the configuration docs.
- If commands or permissions change, update `resources/plugin.yml` and the commands docs.
