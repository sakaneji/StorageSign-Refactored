# Repository Rules

## Read first

- Implementation: `docs/workflow-implementation.md`
- Testing: `docs/workflow-testing.md`
- Docs: `docs/workflow-documentation.md`
- Behavior/config/coverage changes: `README.md` + `docs/documentation-update-guide.md`
- Index/search changes: `docs/storage-sign-index.md`
- Runtime/manual checks: `docs/runtime-validation-checklist.md`
- Coverage gaps: `docs/test-gap-audit.md`

## Working rules

- Inspect current code before editing.
- Use `rg` / `rg --files` for discovery.
- Use `apply_patch` for edits.
- Avoid destructive git commands.
- Keep changes narrow unless behavior changes are requested.

## Verification

- Prefer `./scripts/test.sh`.
- Use the smallest scope that proves the change.
- Run `git diff --check`.
- If public behavior changes, update the related docs in the same change.
