# Repository Rules

## Common

- Inspect code/tests/docs before editing; use `rg` / `rg --files`.
- Use `apply_patch` for manual edits; avoid destructive git commands.
- Keep changes narrow; update docs with public behavior changes.
- Verify with the smallest useful `./scripts/test.sh` scope and `git diff --check`.

## Routing

- `src/main/AGENTS.md`: plugin implementation/resources/runtime.
- `src/test/AGENTS.md`: Java unit/integration tests.
- `tools/AGENTS.md`: Python index/search/offline tools and tests.
- `e2e/AGENTS.md`: E2E harness/bot/runtime checks.
- `docs/AGENTS.md`: docs and README changes.

## Read

- Implementation: `docs/workflow-implementation.md`
- Testing: `docs/workflow-testing.md`
- Docs: `docs/workflow-documentation.md`
- Behavior/config/coverage: `README.md`, `docs/documentation-update-guide.md`
- Index/search/`/sswarp`/nearby-display/external CLI-viewer: `docs/storage-sign-index.md`
- Runtime/manual checks: `docs/runtime-validation-checklist.md`
- Coverage gaps: `docs/test-gap-audit.md`
