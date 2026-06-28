# Implementation Workflow

Read: `AGENTS.md`, `README.md`, `docs/documentation-update-guide.md`, the touched source files, and related tests.  
If the change touches index/search/nearby display, also read `docs/storage-sign-index.md`.

## Usual steps

1. Inspect the current code path first.
2. Patch the smallest set of files.
3. Update docs in the same change when behavior changes.
4. Verify with the narrowest useful test scope.

## Rules

- Prefer `rg` / `rg --files`.
- Use `apply_patch`.
- Do not guess when code or tests can confirm behavior.
- Keep legacy/compat behavior explicit.
- Run `git diff --check`.
