# Testing Workflow

Read: `AGENTS.md`, `docs/test-cases.md`, `docs/test-gap-audit.md`, and `docs/runtime-validation-checklist.md` when runtime behavior is involved.

## Test entry

- Prefer `./scripts/test.sh`.
- Use the smallest proving scope:
  - `unit`
  - `integration`
  - `coverage`
  - `e2e`
  - `banner-compat`
  - `all` only if needed

26.x note:
- 通常の既定実行（version指定なしの `e2e`、引数なしの `banner-compat`、`./scripts/test.sh all`）は、Mineflayer が対応する 1.21.4 / 1.21.8 / 1.21.11 だけを実行する。`banner-compat all` は明示的な全定義版実行として26.xも含む。
- プラグインは 26.1.2 / 26.2 に対応済みで、Java 25での起動と有効化を確認している。
- 26.1.2 / 26.2 は `e2e <version>` または `banner-compat all` で明示実行できるが、保存済み成果物では Mineflayer / `minecraft-protocol` の `unsupported protocol version` でゲームプレイ E2E の完走確認だけが保留中。
- 26.x の通常 E2E / upgrade 完走確認は、そのプロトコル対応を再確認してから再開する。E2E未完走をプラグイン未対応とは扱わない。

## Rules

- Batch verification instead of short polling.
- Check environment issues against the repo docs before changing code.
- Python tooling under `tools/` requires host `python3` 3.10 or newer.
- For index/search/`/sswarp`/nearby-display/external CLI-viewer changes, also read `docs/storage-sign-index.md`.
- Update `docs/test-cases.md` when behavior descriptions, covered cases, or saved-result summaries change.
- Update `docs/runtime-validation-checklist.md` when manual or E2E runtime procedures change.
- Refresh `docs/test-gap-audit.md` when a new `coverage` run changes the reported gaps.
- Finish with `git diff --check`.
