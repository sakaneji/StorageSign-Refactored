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

## Rules

- Batch verification instead of short polling.
- Check environment issues against the repo docs before changing code.
- For search/index changes, also read `docs/storage-sign-index.md`.
- Update `docs/test-cases.md` or `docs/runtime-validation-checklist.md` when behavior changes.
- Finish with `git diff --check`.
